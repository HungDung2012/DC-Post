"""Smoke-check the isolated simulator on ephemeral ports without external services."""

import importlib.util
import json
from pathlib import Path
import threading
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


spec = importlib.util.spec_from_file_location("development_mock", Path(__file__).with_name("server.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def request(base, path, body=None, key=None, timeout=2):
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Idempotency-Key"] = key
    data = None if body is None else json.dumps(body).encode()
    with urlopen(Request(base + path, data=data, headers=headers), timeout=timeout) as response:
        return json.load(response)


def main():
    count = 0
    for scenario in ("success", "duplicate", "rejected", "unknown", "unsupported", "invalid-json",
                     "timeout", "unavailable", "unauthorized"):
        with module.MockServer(("127.0.0.1", 0), scenario, .2) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                assert request(base, "/health")["protocol"] == "development-only"
                count += 1
                if scenario in ("success", "duplicate"):
                    assert request(base, "/dev/pricing/quote", {})["amount"] == 25000
                    assert len(request(base, "/dev/warehouse/locations")["locations"]) == 1
                    assert request(base, "/dev/shipping/submissions/DC1")["status"] == "NOT_FOUND"
                    first = request(base, "/dev/shipping/submissions", {"trackingCode": "DC1"}, "key-1")
                    assert first["outcome"] == "CONFIRMED"
                    assert request(base, "/dev/shipping/submissions", {"trackingCode": "DC1"}, "key-1") == first
                    assert request(base, "/dev/shipping/submissions/DC1")["status"] == "FOUND"
                    assert request(base, "/dev/shipping/tracking/DC1")["trackingCode"] == "DC1"
                    assert request(base, "/dev/shipping/submissions/DC1/cancel", {"reason": "Changed"}, "key-1")["outcome"] == "CONFIRMED"
                    try:
                        request(base, "/dev/shipping/submissions", {"trackingCode": "DC2"}, "key-1")
                        raise AssertionError("Expected conflicting idempotency key")
                    except HTTPError as error:
                        assert error.code == 409
                    count += 9
                elif scenario in ("rejected", "unknown"):
                    assert request(base, "/dev/shipping/submissions", {"trackingCode": "DC1"}, "key-1")["outcome"] == scenario.upper()
                    count += 1
                elif scenario == "unsupported":
                    assert request(base, "/dev/shipping/submissions/DC1")["status"] == "UNSUPPORTED"
                    count += 1
                elif scenario == "invalid-json":
                    try:
                        request(base, "/dev/pricing/quote", {})
                        raise AssertionError("Expected invalid JSON")
                    except json.JSONDecodeError:
                        count += 1
                elif scenario == "timeout":
                    try:
                        request(base, "/dev/shipping/submissions", {"trackingCode": "DC1"}, "key-1", timeout=.05)
                        raise AssertionError("Expected timeout")
                    except (TimeoutError, URLError):
                        with server.lock:
                            assert "DC1" in server.submissions
                        count += 1
                else:
                    try:
                        request(base, "/dev/pricing/quote", {})
                        raise AssertionError("Expected HTTP failure")
                    except HTTPError as error:
                        assert error.code == (503 if scenario == "unavailable" else 401)
                        count += 1
            finally:
                server.shutdown()
                worker.join()
    print(f"Mock smoke: {count} checks passed across 9 scenarios")


if __name__ == "__main__":
    main()
