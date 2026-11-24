package com.dcpost.customer.integration;

import com.dcpost.customer.integration.pricing.PricingHttpAdapter;
import com.dcpost.customer.integration.pricing.dto.FeeRequest;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.integration.warehouse.WarehouseHttpAdapter;
import com.dcpost.customer.integration.shipping.ShippingHttpAdapter;
import com.dcpost.customer.integration.shipping.dto.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class DevelopmentAdapterTest {
    private HttpServer server;
    private String baseUrl;
    private final Instant now = Instant.parse("2026-11-24T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
    }

    private PricingHttpAdapter pricing() {
        return new PricingHttpAdapter(baseUrl, Duration.ofMillis(200), Duration.ofMillis(200), clock);
    }

    private FeeRequest feeRequest() {
        return new FeeRequest("Origin", "Destination", BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
    }

    /** Maps a real HTTP quote using exact VND and the local receipt time. */
    @Test
    void Test_quote_001() {
        respond("/dev/pricing/quote", 200, "{\"amount\":25000,\"currency\":\"VND\"}");
        var quote = pricing().quote(feeRequest());
        assertThat(quote).isNotNull();
        assertThat(quote.amount()).isEqualByComparingTo("25000");
        assertThat(quote.currency()).isEqualTo("VND");
        assertThat(quote.receivedAt()).isEqualTo(now);
    }

    /** Rejects malformed JSON rather than using a fabricated fee. */
    @Test
    void Test_quote_002() {
        respond("/dev/pricing/quote", 200, "invalid");
        assertThatThrownBy(() -> pricing().quote(feeRequest()))
                .isInstanceOfSatisfying(IntegrationException.class,
                        error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE));
    }

    /** Rejects negative, fractional, nonnumeric, missing or foreign-currency fees. */
    @Test
    void Test_quote_003() {
        for (String body : new String[] {"{}", "{\"amount\":-1,\"currency\":\"VND\"}",
                "{\"amount\":1.5,\"currency\":\"VND\"}", "{\"amount\":\"oops\",\"currency\":\"VND\"}",
                "{\"amount\":1,\"currency\":\"USD\"}"}) {
            respond("/dev/pricing/quote", 200, body);
            assertThatThrownBy(() -> pricing().quote(feeRequest())).isInstanceOf(IntegrationException.class);
            server.removeContext("/dev/pricing/quote");
        }
    }

    /** Distinguishes authorization and availability errors without exposing the external body. */
    @Test
    void Test_quote_004() {
        for (int status : new int[] {401, 403, 503}) {
            respond("/dev/pricing/quote", status, "private upstream details");
            assertThatThrownBy(() -> pricing().quote(feeRequest()))
                    .isInstanceOfSatisfying(IntegrationException.class, error -> {
                        assertThat(error.kind()).isEqualTo(status == 503
                                ? IntegrationException.Kind.UNAVAILABLE : IntegrationException.Kind.UNAUTHORIZED);
                        assertThat(error.getMessage()).doesNotContain("private");
                    });
            server.removeContext("/dev/pricing/quote");
        }
    }

    /** A slow HTTP response produces a read timeout, with no automatic retry. */
    @Test
    void Test_quote_005() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/dev/pricing/quote", exchange -> {
            calls.incrementAndGet();
            try { Thread.sleep(600); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        assertThatThrownBy(() -> pricing().quote(feeRequest()))
                .isInstanceOfSatisfying(IntegrationException.class,
                        error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.READ_TIMEOUT));
        assertThat(calls.get()).isEqualTo(1);
    }

    /** Tiny fractions must not disappear through floating-point JSON parsing. */
    @Test
    void Test_quote_006() {
        respond("/dev/pricing/quote", 200, "{\"amount\":25000.0000000000001,\"currency\":\"VND\"}");
        assertThatThrownBy(() -> pricing().quote(feeRequest()))
                .isInstanceOfSatisfying(IntegrationException.class,
                        error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE));
    }

    /** Integer-valued decimal JSON preserves every digit, even beyond double precision. */
    @Test
    void Test_quote_007() {
        respond("/dev/pricing/quote", 200, "{\"amount\":9007199254740993.0,\"currency\":\"VND\"}");
        assertThat(pricing().quote(feeRequest()).amount()).isEqualByComparingTo("9007199254740993");
    }

    private ShippingHttpAdapter shipping() {
        return new ShippingHttpAdapter(baseUrl, Duration.ofMillis(200), Duration.ofMillis(200));
    }

    private ShipmentSubmission submission() {
        return new ShipmentSubmission("DC123", new ShipmentSubmission.Contact("Sender", "0900000000", "Origin"),
                new ShipmentSubmission.Contact("Recipient", "0911111111", "Destination"),
                new ShipmentSubmission.Parcel(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                "DROP_OFF", "warehouse-A", null, java.util.List.of(
                        new ShipmentSubmission.Item("Book", "Books", 1, BigDecimal.TEN, null)));
    }

    /** Keeps opaque external location identifiers without coercing them to numbers. */
    @Test
    void Test_getDropOffLocations_001() {
        respond("/dev/warehouse/locations", 200,
                "{\"locations\":[{\"id\":\"warehouse-A\",\"name\":\"Branch\",\"address\":\"Area A\"}]}");
        var locations = new WarehouseHttpAdapter(baseUrl, Duration.ofMillis(200), Duration.ofMillis(200))
                .getDropOffLocations();
        assertThat(locations).isNotNull().hasSize(1);
        assertThat(locations.getFirst().externalId()).isEqualTo("warehouse-A");
    }

    /** Malformed locations must not silently become an empty directory. */
    @Test
    void Test_getDropOffLocations_002() {
        respond("/dev/warehouse/locations", 200, "{\"locations\":[{\"name\":\"Branch\",\"address\":\"Area\"}]}");
        assertThatThrownBy(() -> new WarehouseHttpAdapter(baseUrl, Duration.ofMillis(200), Duration.ofMillis(200))
                .getDropOffLocations()).isInstanceOfSatisfying(IntegrationException.class,
                        error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE));
    }

    /** Sends the caller's idempotency key and snapshot over HTTP. */
    @Test
    void Test_submit_001() {
        var receivedKey = new java.util.concurrent.atomic.AtomicReference<String>();
        var receivedBody = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/dev/shipping/submissions", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"outcome\":\"CONFIRMED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        assertThat(shipping().submit(submission(), "create-1")).isNotNull()
                .extracting(SubmissionResult::outcome).isEqualTo(OperationOutcome.CONFIRMED);
        assertThat(receivedKey.get()).isEqualTo("create-1");
        assertThat(receivedBody.get()).contains("DC123", "warehouse-A", "Book");
    }

    /** A malformed mutation response never authorizes an automatic resend. */
    @Test
    void Test_submit_002() {
        respond("/dev/shipping/submissions", 200, "{}");
        assertThatThrownBy(() -> shipping().submit(submission(), "create-1"))
                .isInstanceOfSatisfying(IntegrationException.class, error -> {
                    assertThat(error.kind()).isEqualTo(IntegrationException.Kind.UNKNOWN_OUTCOME);
                    assertThat(error.failureKind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE);
                });
    }

    /** A timed-out submission stays unknown and is sent exactly once. */
    @Test
    void Test_submit_003() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/dev/shipping/submissions", exchange -> {
            calls.incrementAndGet();
            try { Thread.sleep(600); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        assertThatThrownBy(() -> shipping().submit(submission(), "create-1"))
                .isInstanceOfSatisfying(IntegrationException.class, error -> {
                    assertThat(error.kind()).isEqualTo(IntegrationException.Kind.UNKNOWN_OUTCOME);
                    assertThat(error.failureKind()).isEqualTo(IntegrationException.Kind.READ_TIMEOUT);
                });
        assertThat(calls.get()).isEqualTo(1);
    }

    /** Separates a missing lookup capability from an authoritative absence of a submission. */
    @Test
    void Test_findSubmission_001() {
        for (String status : new String[] {"FOUND", "NOT_FOUND", "UNKNOWN", "UNSUPPORTED"}) {
            respond("/dev/shipping/submissions/DC123", 200, "{\"status\":\"" + status + "\"}");
            assertThat(shipping().findSubmission("DC123")).isNotNull()
                    .extracting(SubmissionLookup::status).isEqualTo(SubmissionLookup.Status.valueOf(status));
            server.removeContext("/dev/shipping/submissions/DC123");
        }
    }

    /** HTTP errors and invalid lookup statuses never become an authoritative NOT_FOUND. */
    @Test
    void Test_findSubmission_002() {
        respond("/dev/shipping/submissions/DC123", 404, "{\"status\":\"NOT_FOUND\"}");
        assertThatThrownBy(() -> shipping().findSubmission("DC123"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.UNAVAILABLE));
        server.removeContext("/dev/shipping/submissions/DC123");
        respond("/dev/shipping/submissions/DC123", 200, "{\"status\":\"NEW_EXTERNAL_CODE\"}");
        assertThatThrownBy(() -> shipping().findSubmission("DC123"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE));
    }

    /** Maps only explicit cancellation outcomes; an unrecognized result stays unknown. */
    @Test
    void Test_cancel_001() {
        for (String outcome : new String[] {"CONFIRMED", "REJECTED", "UNKNOWN"}) {
            respond("/dev/shipping/submissions/DC123/cancel", 200, "{\"outcome\":\"" + outcome + "\"}");
            assertThat(shipping().cancel("DC123", "cancel-1", "Changed plans")).isNotNull()
                    .extracting(CancellationResult::outcome).isEqualTo(OperationOutcome.valueOf(outcome));
            server.removeContext("/dev/shipping/submissions/DC123/cancel");
        }
    }

    /** A cancellation with an unrecognized response retains an unknown outcome. */
    @Test
    void Test_cancel_002() {
        respond("/dev/shipping/submissions/DC123/cancel", 200, "{\"outcome\":\"NEW_EXTERNAL_CODE\"}");
        assertThatThrownBy(() -> shipping().cancel("DC123", "cancel-1", "Changed plans"))
                .isInstanceOfSatisfying(IntegrationException.class, error -> {
                    assertThat(error.kind()).isEqualTo(IntegrationException.Kind.UNKNOWN_OUTCOME);
                    assertThat(error.failureKind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE);
                });
    }

    /** Keeps private recipient details outside the public event DTO. */
    @Test
    void Test_getTracking_001() {
        respond("/dev/shipping/tracking/DC123", 200,
                "{\"trackingCode\":\"DC123\",\"sourceStatus\":\"SOURCE_CODE\",\"updatedAt\":\"2026-11-24T00:00:00Z\","
                + "\"publicEvents\":[{\"sourceStatus\":\"SOURCE_CODE\",\"occurredAt\":\"2026-11-24T00:00:00Z\",\"area\":\"District\"}],"
                + "\"privateDetails\":{\"recipientName\":\"Private\",\"recipientPhone\":\"0900000000\",\"recipientAddress\":\"Private address\"}}");
        var tracking = shipping().getTracking("DC123");
        assertThat(tracking).isNotNull();
        assertThat(tracking.sourceStatus()).isEqualTo("SOURCE_CODE");
        assertThat(tracking.publicEvents()).hasSize(1);
        assertThat(tracking.publicEvents().toString()).doesNotContain("Private", "0900000000");
        assertThat(tracking.privateDetails().recipientName()).isEqualTo("Private");
    }

    /** Rejects tracking from a different shipment instead of mixing private data. */
    @Test
    void Test_getTracking_002() {
        respond("/dev/shipping/tracking/DC123", 200,
                "{\"trackingCode\":\"OTHER\",\"sourceStatus\":\"SOURCE_CODE\",\"updatedAt\":\"2026-11-24T00:00:00Z\",\"publicEvents\":[]}");
        assertThatThrownBy(() -> shipping().getTracking("DC123")).isInstanceOf(IntegrationException.class);
    }
}
