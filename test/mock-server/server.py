"""Isolated development protocol simulator. This is not a Go API contract."""

import argparse
import copy
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import time
from urllib.parse import unquote, urlsplit


FIXTURES = Path(__file__).resolve().parent / "fixtures"


class MockServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, scenario, delay):
        super().__init__(address, Handler)
        self.scenario = scenario
        self.delay = delay
        self.fixtures = {name: json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))
                         for name in ("pricing", "warehouse", "shipping")}
        self.lock = threading.Lock()
        self.submissions = {}
        self.requests = {}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Request bodies, keys and recipient details must not appear in logs.
        pass

    def do_GET(self):
        self.handle_request()

    def do_POST(self):
        self.handle_request()

    def handle_request(self):
        path = unquote(urlsplit(self.path).path)
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size < 0 or size > 1024 * 1024:
                self.reply(413, {"error": "request_too_large"})
                return
            body = json.loads(self.rfile.read(size)) if size else {}
            if not isinstance(body, dict):
                raise ValueError("object required")
        except (ValueError, UnicodeDecodeError):
            self.reply(400, {"error": "invalid_request"})
            return

        if path == "/health" and self.command == "GET":
            self.reply(200, {"status": "UP", "protocol": "development-only"})
            return
        scenario = self.server.scenario
        if scenario == "unauthorized":
            self.reply(401, {"error": "development_unauthorized"})
            return
        if scenario == "unavailable":
            self.reply(503, {"error": "development_unavailable"})
            return

        status, result = self.route(path, body)
        # Apply the mutation first: a timeout must not imply that nothing happened.
        if scenario == "timeout":
            time.sleep(self.server.delay)
        self.reply(status, result, invalid=scenario == "invalid-json")

    def route(self, path, body):
        fixtures = self.server.fixtures
        scenario = self.server.scenario
        if path == "/dev/pricing/quote" and self.command == "POST":
            return 200, fixtures["pricing"]
        if path == "/dev/warehouse/locations" and self.command == "GET":
            return 200, fixtures["warehouse"]
        if path == "/dev/shipping/submissions" and self.command == "POST":
            code = body.get("trackingCode")
            if not isinstance(code, str) or not code:
                return 400, {"error": "tracking_code_required"}
            return self.mutate("submit", code, body)
        prefix = "/dev/shipping/submissions/"
        if path.startswith(prefix):
            suffix = path[len(prefix):]
            if suffix.endswith("/cancel") and self.command == "POST":
                return self.mutate("cancel", suffix[:-7], body)
            if "/" not in suffix and self.command == "GET":
                if scenario == "unsupported":
                    return 200, {"status": "UNSUPPORTED"}
                if scenario == "unknown":
                    return 200, {"status": "UNKNOWN"}
                with self.server.lock:
                    return 200, {"status": "FOUND" if suffix in self.server.submissions else "NOT_FOUND"}
        prefix = "/dev/shipping/tracking/"
        if path.startswith(prefix) and self.command == "GET":
            code = path[len(prefix):]
            with self.server.lock:
                snapshot = self.server.submissions.get(code)
                if snapshot is None:
                    return 404, {"error": "not_found"}
                result = copy.deepcopy(fixtures["shipping"]["tracking"])
                result["trackingCode"] = code
                recipient = snapshot.get("recipient", {})
                result["privateDetails"] = {
                    "recipientName": recipient.get("name", "Development recipient"),
                    "recipientPhone": recipient.get("phone", "0900000000"),
                    "recipientAddress": recipient.get("address", "Development address")}
                return 200, result
        return 404, {"error": "unknown_development_route"}

    def mutate(self, operation, code, body):
        key = self.headers.get("Idempotency-Key")
        if not key:
            return 400, {"error": "idempotency_key_required"}
        canonical = json.dumps([code, body], sort_keys=True, separators=(",", ":"))
        with self.server.lock:
            previous = self.server.requests.get((operation, key))
            if previous is not None:
                if previous[0] != canonical:
                    return 409, {"error": "idempotency_conflict"}
                return 200, previous[1]
            outcome = {"rejected": "REJECTED", "unknown": "UNKNOWN"}.get(self.server.scenario, "CONFIRMED")
            fixture = "submission" if operation == "submit" else "cancellation"
            result = copy.deepcopy(self.server.fixtures["shipping"][fixture])
            result["outcome"] = outcome
            if self.server.scenario == "duplicate":
                result["duplicate"] = True
            if outcome == "CONFIRMED" and operation == "submit":
                existing = self.server.submissions.get(code)
                if existing is not None and existing != body:
                    return 409, {"error": "tracking_conflict"}
                self.server.submissions[code] = body
            self.server.requests[(operation, key)] = (canonical, result)
            return 200, result

    def reply(self, status, body, invalid=False):
        payload = b"invalid-json" if invalid else json.dumps(body).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--scenario", default="success", choices=[
        "success", "timeout", "invalid-json", "duplicate", "rejected", "unknown", "unsupported",
        "unavailable", "unauthorized"])
    parser.add_argument("--delay", type=float, default=12, help="Response delay in seconds for timeout scenario")
    args = parser.parse_args()
    if args.delay < 0:
        parser.error("--delay must be non-negative")
    with MockServer((args.host, args.port), args.scenario, args.delay) as server:
        print(f"Development-only mock listening on {server.server_address[0]}:{server.server_address[1]}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
