package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.shipping.ShippingHttpAdapter;
import com.dcpost.customer.integration.shipping.dto.SubmissionLookup;
import com.dcpost.customer.integration.shipping.dto.SubmissionResult;
import com.dcpost.customer.integration.shipping.dto.OperationOutcome;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.service.*;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.repository.UserRepository;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.dcpost.customer.shipment.ShipmentCreationServiceTest.request;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentRecoveryIT {
    @Autowired ShipmentSyncStateService state;
    @Autowired ShipmentWriteService writer;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ShippingClient shipping;
    CustomerPrincipal customer;
    final Instant now = Instant.parse("2026-11-26T10:00:00Z");

    @BeforeEach void setup() {
        var user = users.saveAndFlush(new User("Recovery IT", UUID.randomUUID() + "@example.invalid",
                "0" + String.format("%010d", ThreadLocalRandom.current().nextLong(10000000000L)), "hash", Instant.now()));
        customer = new CustomerPrincipal(user.getId(), 0);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE i FROM shipment_items i JOIN shipments s ON i.shipment_id=s.shipment_id WHERE s.customer_id=?", customer.userId());
        jdbc.update("DELETE FROM shipments WHERE customer_id=?", customer.userId());
        users.deleteById(customer.userId());
    }

    long seed() {
        var payload = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        long id = writer.create(customer.userId(), Math.toIntExact(customer.userId()), "customer:" + customer.userId(),
                UUID.randomUUID().toString(), "a".repeat(64), payload, payload.confirmedFee()).shipmentId();
        jdbc.update("UPDATE shipments SET sync_next_attempt_at=NULL WHERE shipment_id=?", id);
        return id;
    }

    String status(long id) { return jdbc.queryForObject("SELECT sync_status FROM shipments WHERE shipment_id=?", String.class, id); }
    String code(long id) { return jdbc.queryForObject("SELECT tracking_code FROM shipments WHERE shipment_id=?", String.class, id); }
    String error(long id) { return jdbc.queryForObject("SELECT sync_last_error_code FROM shipments WHERE shipment_id=?", String.class, id); }
    int attempts(long id) { return jdbc.queryForObject("SELECT sync_attempt_count FROM shipments WHERE shipment_id=?", Integer.class, id); }
    ShipmentSyncService worker(Instant time) { return new ShipmentSyncService(state, shipping, Clock.fixed(time, ZoneOffset.UTC)); }

    void assertNext(long id, Instant expected) {
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(sync_next_attempt_at,'%Y-%m-%d %H:%i:%s') FROM shipments WHERE shipment_id=?", String.class, id))
                .isEqualTo(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(expected));
    }

    void interrupted(long id, String started) {
        jdbc.update("UPDATE shipments SET sync_status='PROCESSING',sync_attempt_count=1,sync_started_at=?,sync_next_attempt_at=NULL WHERE shipment_id=?", started, id);
    }

    /** A new worker recovers persisted expired PROCESSING through lookup, without resubmitting the uncertain mutation. */
    @Test void Test_recover_001() {
        long id = seed();
        interrupted(id, "2026-11-26 09:00:00");
        when(shipping.findSubmission(code(id))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(status(id)).isEqualTo("UNKNOWN");
            return new SubmissionLookup(SubmissionLookup.Status.UNKNOWN);
        });
        worker(now).recover(id);
        assertThat(status(id)).isEqualTo("UNKNOWN");
        assertThat(error(id)).isEqualTo("SHIPPING_LOOKUP_UNKNOWN");
        assertThat(attempts(id)).isEqualTo(1);
        verify(shipping).findSubmission(code(id));
        verify(shipping, never()).submit(any(), anyString());
    }

    /** A still-active worker is not expired or queried before its processing deadline. */
    @Test void Test_recover_002() {
        long id = seed(); interrupted(id, "2026-11-26 09:59:00");
        worker(now).recover(id);
        assertThat(status(id)).isEqualTo("PROCESSING");
        verifyNoInteractions(shipping);
    }

    /** A found submission completes recovery without another shipping mutation. */
    @Test void Test_recover_003() {
        long id = seed(); interrupted(id, "2026-11-26 09:00:00");
        when(shipping.findSubmission(code(id))).thenReturn(new SubmissionLookup(SubmissionLookup.Status.FOUND));
        worker(now).recover(id); worker(now.plusSeconds(120)).sync(id);
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(error(id)).isNull();
        assertThat(attempts(id)).isEqualTo(1);
        verify(shipping, never()).submit(any(), anyString());
    }

    /** NOT_FOUND without a safe retry guarantee and UNSUPPORTED stop mutation retries explicitly. */
    @Test void Test_recover_004() {
        for (var result : new SubmissionLookup.Status[] {SubmissionLookup.Status.NOT_FOUND, SubmissionLookup.Status.UNSUPPORTED}) {
            long id = seed(); interrupted(id, "2026-11-26 09:00:00");
            when(shipping.findSubmission(code(id))).thenReturn(new SubmissionLookup(result));
            worker(now).recover(id); worker(now.plusSeconds(120)).sync(id);
            assertThat(status(id)).isEqualTo("BLOCKED");
            assertThat(error(id)).isEqualTo(result == SubmissionLookup.Status.NOT_FOUND ? "SHIPPING_RETRY_UNSUPPORTED" : "SHIPPING_LOOKUP_UNSUPPORTED");
        }
        verify(shipping, never()).submit(any(), anyString());
    }

    /** Only a verified adapter retry guarantee plus NOT_FOUND permits one delayed retry using the same key. */
    @Test void Test_recover_005() {
        long id = seed(); interrupted(id, "2026-11-26 09:00:00");
        when(shipping.supportsSafeResubmission()).thenReturn(true);
        when(shipping.findSubmission(code(id))).thenReturn(new SubmissionLookup(SubmissionLookup.Status.NOT_FOUND));
        when(shipping.submit(any(), anyString())).thenReturn(new SubmissionResult(OperationOutcome.CONFIRMED));
        worker(now).recover(id);
        assertThat(status(id)).isEqualTo("PENDING");
        assertNext(id, now.plusSeconds(30));
        worker(now.plusSeconds(29)).sync(id);
        verify(shipping, never()).submit(any(), anyString());
        worker(now.plusSeconds(30)).sync(id);
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(attempts(id)).isEqualTo(2);
        verify(shipping, times(1)).submit(any(), eq("shipment:" + code(id)));
    }

    /** A failed lookup keeps uncertainty and waits before the next read; auth failure blocks it. */
    @Test void Test_recover_006() {
        for (var kind : IntegrationException.Kind.values()) {
            long id = seed(); interrupted(id, "2026-11-26 09:00:00");
            doThrow(new IntegrationException(kind, false)).when(shipping).findSubmission(code(id));
            assertThatCode(() -> worker(now).recover(id)).doesNotThrowAnyException();
            assertThat(status(id)).isEqualTo(kind == IntegrationException.Kind.UNAUTHORIZED ? "BLOCKED" : "UNKNOWN");
            assertThat(error(id)).isEqualTo("EXTERNAL_" + kind.name());
            worker(now.plusSeconds(59)).recover(id);
            verify(shipping, times(1)).findSubmission(code(id));
            if (kind != IntegrationException.Kind.UNAUTHORIZED) assertNext(id, now.plusSeconds(60));
        }
        verify(shipping, never()).submit(any(), anyString());
    }

    /** Null or malformed lookup results are invalid responses and never evidence that a shipment was not received. */
    @Test void Test_recover_007() {
        for (var result : java.util.Arrays.asList((SubmissionLookup) null, new SubmissionLookup(null))) {
            long id = seed(); interrupted(id, "2026-11-26 09:00:00");
            when(shipping.findSubmission(code(id))).thenReturn(result);
            worker(now).recover(id);
            assertThat(status(id)).isEqualTo("UNKNOWN");
            assertThat(error(id)).isEqualTo("EXTERNAL_INVALID_RESPONSE");
            assertNext(id, now.plusSeconds(60));
        }
        verify(shipping, never()).submit(any(), anyString());
    }

    /** A connect timeout before sending retries with exponential delays and stops at the configured send limit. */
    @Test void Test_sync_001() {
        long id = seed();
        doThrow(new IntegrationException(IntegrationException.Kind.CONNECT_TIMEOUT, true)).when(shipping).submit(any(), anyString());
        var time = now;
        for (int attempt = 1; attempt <= 5; attempt++) {
            worker(time).sync(id);
            assertThat(attempts(id)).isEqualTo(attempt);
            assertThat(status(id)).isEqualTo(attempt == 5 ? "BLOCKED" : "PENDING");
            assertThat(error(id)).isEqualTo(attempt == 5 ? "SYNC_ATTEMPTS_EXHAUSTED" : "EXTERNAL_CONNECT_TIMEOUT");
            if (attempt < 5) {
                long delay = 30L << (attempt - 1);
                assertNext(id, time.plusSeconds(delay));
                worker(time.plusSeconds(delay - 1)).sync(id);
                assertThat(attempts(id)).isEqualTo(attempt);
                time = time.plusSeconds(delay);
            }
        }
        worker(time.plusSeconds(3600)).sync(id);
        verify(shipping, times(5)).submit(any(), eq("shipment:" + code(id)));
        verify(shipping, never()).findSubmission(anyString());
    }

    /** Possibly accepted requests keep UNKNOWN and wait for lookup instead of a mutation retry. */
    @Test void Test_sync_002() {
        for (var kind : new IntegrationException.Kind[] {IntegrationException.Kind.READ_TIMEOUT, IntegrationException.Kind.UNAVAILABLE,
                IntegrationException.Kind.INVALID_RESPONSE, IntegrationException.Kind.UNKNOWN_OUTCOME}) {
            long id = seed();
            doThrow(new IntegrationException(kind, true)).when(shipping).submit(any(), anyString());
            worker(now).sync(id);
            assertThat(status(id)).isEqualTo("UNKNOWN");
            assertThat(error(id)).isEqualTo("EXTERNAL_" + kind.name());
            assertNext(id, now.plusSeconds(60));
            worker(now.plusSeconds(3600)).sync(id);
            assertThat(attempts(id)).isEqualTo(1);
        }
        verify(shipping, times(4)).submit(any(), anyString());
    }

    /** Null and unknown submit responses schedule reconciliation, without pretending a definite failure. */
    @Test void Test_sync_003() {
        for (var result : java.util.Arrays.asList((SubmissionResult) null, new SubmissionResult(null), new SubmissionResult(OperationOutcome.UNKNOWN))) {
            long id = seed();
            when(shipping.submit(any(), anyString())).thenReturn(result);
            worker(now).sync(id);
            assertThat(status(id)).isEqualTo("UNKNOWN");
            assertNext(id, now.plusSeconds(60));
            worker(now.plusSeconds(3600)).sync(id);
            assertThat(attempts(id)).isEqualTo(1);
        }
        verify(shipping, times(3)).submit(any(), anyString());
    }

    /** Persisted exhausted PENDING work is blocked without sending even if a new process discovers it. */
    @Test void Test_sync_004() {
        long id = seed();
        jdbc.update("UPDATE shipments SET sync_attempt_count=5 WHERE shipment_id=?", id);
        worker(now).sync(id);
        assertThat(status(id)).isEqualTo("BLOCKED");
        assertThat(error(id)).isEqualTo("SYNC_ATTEMPTS_EXHAUSTED");
        assertThat(attempts(id)).isEqualTo(5);
        verifyNoInteractions(shipping);
    }

    /** Recovery scans are bounded and select only expired processing or due unknown rows. */
    @Test void Test_recoveryCandidates_001() {
        long expired = seed(); interrupted(expired, "2026-11-26 09:58:00");
        long active = seed(); interrupted(active, "2026-11-26 09:58:01");
        long due = seed();
        jdbc.update("UPDATE shipments SET sync_status='UNKNOWN',sync_next_attempt_at='2026-11-26 10:00:00' WHERE shipment_id=?", due);
        long future = seed();
        jdbc.update("UPDATE shipments SET sync_status='UNKNOWN',sync_next_attempt_at='2026-11-26 10:00:01' WHERE shipment_id=?", future);
        long pending = seed();
        assertThat(state.recoveryCandidates(now, 1)).containsExactly(expired);
        assertThat(state.recoveryCandidates(now, 100)).containsExactly(expired, due);
        assertThat(status(active)).isEqualTo("PROCESSING");
        assertThat(status(pending)).isEqualTo("PENDING");
        verifyNoInteractions(shipping);
    }

    /** Only one concurrent recovery worker owns a lookup lease while the request is in flight. */
    @Test void Test_recover_008() throws Exception {
        long id = seed(); interrupted(id, "2026-11-26 09:00:00");
        var start = new CyclicBarrier(2); var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1); var completed = new CountDownLatch(1);
        when(shipping.findSubmission(code(id))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new SubmissionLookup(SubmissionLookup.Status.FOUND);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> task = () -> { start.await(10, TimeUnit.SECONDS); worker(now).recover(id); completed.countDown(); return null; };
            var first = executor.submit(task); var second = executor.submit(task);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
                verify(shipping, times(1)).findSubmission(code(id));
                assertThat(status(id)).isEqualTo("UNKNOWN");
            } finally { release.countDown(); }
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        }
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(attempts(id)).isEqualTo(1);
    }

    /** An expired lookup lease can be taken over and its old response cannot schedule a duplicate mutation. */
    @Test void Test_recover_009() {
        long id = seed(); interrupted(id, "2026-11-26 09:00:00");
        var oldClaim = state.tryRecoveryClaim(id, now).orElseThrow();
        assertThat(state.tryRecoveryClaim(id, now.plusSeconds(119))).isEmpty();
        var newClaim = state.tryRecoveryClaim(id, now.plusSeconds(120)).orElseThrow();
        state.finishRecovery(newClaim, SubmissionLookup.Status.FOUND, false, now.plusSeconds(120));
        state.finishRecovery(oldClaim, SubmissionLookup.Status.NOT_FOUND, true, now.plusSeconds(121));
        state.failRecovery(oldClaim, IntegrationException.Kind.UNAUTHORIZED, now.plusSeconds(121));
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(error(id)).isNull();
        assertThat(attempts(id)).isEqualTo(1);
        verifyNoInteractions(shipping);
    }

    /** Eligibility is rechecked under the shipment lock after lookup when cancellation or payment changes in flight. */
    @Test void Test_recover_010() {
        when(shipping.supportsSafeResubmission()).thenReturn(true);
        for (var change : new String[] {"cancellation_status='REQUESTED'", "cancellation_status='CONFIRMED'",
                "cancelled_at='2026-11-26 10:00:00'", "payment_method='VNPAY'"}) {
            long id = seed(); interrupted(id, "2026-11-26 09:00:00");
            when(shipping.findSubmission(code(id))).thenAnswer(call -> {
                jdbc.update("UPDATE shipments SET " + change + " WHERE shipment_id=?", id);
                return new SubmissionLookup(SubmissionLookup.Status.NOT_FOUND);
            });
            worker(now).recover(id);
            assertThat(status(id)).isEqualTo("BLOCKED");
            assertThat(error(id)).isEqualTo("SYNC_RETRY_NOT_ELIGIBLE");
            worker(now.plusSeconds(3600)).sync(id);
        }
        verify(shipping, never()).submit(any(), anyString());
    }

    /** Send exhaustion still permits acceptance lookup, but NOT_FOUND cannot replenish the send budget. */
    @Test void Test_recover_011() {
        when(shipping.supportsSafeResubmission()).thenReturn(true);
        for (var result : new SubmissionLookup.Status[] {SubmissionLookup.Status.FOUND, SubmissionLookup.Status.NOT_FOUND}) {
            long id = seed(); interrupted(id, "2026-11-26 09:00:00");
            jdbc.update("UPDATE shipments SET sync_attempt_count=5 WHERE shipment_id=?", id);
            when(shipping.findSubmission(code(id))).thenReturn(new SubmissionLookup(result));
            worker(now).recover(id);
            assertThat(status(id)).isEqualTo(result == SubmissionLookup.Status.FOUND ? "SYNCED" : "BLOCKED");
            if (result == SubmissionLookup.Status.NOT_FOUND) assertThat(error(id)).isEqualTo("SYNC_ATTEMPTS_EXHAUSTED");
            assertThat(attempts(id)).isEqualTo(5);
        }
        verify(shipping, never()).submit(any(), anyString());
    }

    /** Real HTTP failures after receiving a request are reconciled through lookup, without repeating the POST. */
    @Test void Test_sync_005() throws Exception {
        for (var mode : new String[] {"HTTP_500", "MALFORMED", "READ_TIMEOUT"}) {
            long id = seed();
            var received = new CountDownLatch(1); var release = new CountDownLatch(1);
            var posts = new AtomicInteger();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/dev/shipping/submissions", exchange -> {
                try (exchange) {
                    String response;
                    int statusCode = 200;
                    if (exchange.getRequestMethod().equals("GET")) response = "{\"status\":\"FOUND\"}";
                    else {
                        exchange.getRequestBody().readAllBytes(); posts.incrementAndGet(); received.countDown();
                        if (mode.equals("READ_TIMEOUT")) {
                            try { release.await(10, TimeUnit.SECONDS); }
                            catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
                        }
                        statusCode = mode.equals("HTTP_500") ? 500 : 200;
                        response = mode.equals("MALFORMED") ? "{\"outcome\":\"INVALID\"}" : "{\"outcome\":\"CONFIRMED\"}";
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(statusCode, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (java.io.IOException ignored) { /* A timed-out HTTP client may close before the reply. */ }
            });
            server.start();
            try {
                var adapter = new ShippingHttpAdapter("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofMillis(500));
                doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return adapter.submit(call.getArgument(0), call.getArgument(1));
                }).when(shipping).submit(any(), anyString());
                doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return adapter.findSubmission(call.getArgument(0));
                }).when(shipping).findSubmission(code(id));
                worker(now).sync(id);
                assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(status(id)).isEqualTo("UNKNOWN");
                assertThat(error(id)).isEqualTo("EXTERNAL_" + (mode.equals("HTTP_500") ? "UNAVAILABLE"
                        : mode.equals("MALFORMED") ? "INVALID_RESPONSE" : "READ_TIMEOUT"));
                release.countDown();
                worker(now.plusSeconds(60)).recover(id);
                assertThat(status(id)).isEqualTo("SYNCED");
                assertThat(attempts(id)).isEqualTo(1);
                assertThat(posts.get()).isEqualTo(1);
            } finally { release.countDown(); server.stop(0); }
        }
    }
}
