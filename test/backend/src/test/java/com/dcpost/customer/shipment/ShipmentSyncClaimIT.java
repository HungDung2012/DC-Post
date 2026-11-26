package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.shipping.ShippingHttpAdapter;
import com.dcpost.customer.integration.shipping.dto.*;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.service.*;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.repository.UserRepository;
import java.time.Instant;
import java.time.Duration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.dcpost.customer.shipment.ShipmentCreationServiceTest.request;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentSyncClaimIT {
    @Autowired ShipmentSyncService sync;
    @Autowired ShipmentSyncStateService state;
    @Autowired ShipmentWriteService writer;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean ShippingClient shipping;
    CustomerPrincipal customer;
    final List<Long> guestIds = new ArrayList<>();

    @BeforeEach void setup() {
        var user = users.saveAndFlush(new User("Sync IT", UUID.randomUUID() + "@example.invalid",
                "0" + String.format("%010d", ThreadLocalRandom.current().nextLong(10000000000L)), "hash", Instant.now()));
        customer = new CustomerPrincipal(user.getId(), 0);
    }

    @AfterEach void cleanup() {
        for (long id : guestIds) {
            jdbc.update("DELETE FROM shipment_items WHERE shipment_id=?", id);
            jdbc.update("DELETE FROM shipments WHERE shipment_id=?", id);
        }
        jdbc.update("DELETE i FROM shipment_items i JOIN shipments s ON s.shipment_id=i.shipment_id WHERE s.customer_id=?", customer.userId());
        jdbc.update("DELETE FROM shipments WHERE customer_id=?", customer.userId());
        users.deleteById(customer.userId());
    }

    long seed(PaymentMethod method, PickupMethod pickup) {
        var payload = request(pickup, method);
        return writer.create(customer.userId(), Math.toIntExact(customer.userId()), "customer:" + customer.userId(),
                UUID.randomUUID().toString(), "a".repeat(64), payload, payload.confirmedFee()).shipmentId();
    }

    String status(long id) {
        return jdbc.queryForObject("SELECT sync_status FROM shipments WHERE shipment_id=?", String.class, id);
    }

    int attempts(long id) {
        return jdbc.queryForObject("SELECT sync_attempt_count FROM shipments WHERE shipment_id=?", Integer.class, id);
    }

    /** CASH commits PROCESSING before one external call and persists CONFIRMED in a new transaction. */
    @Test void Test_sync_001() {
        long id = seed(PaymentMethod.CASH, PickupMethod.DROP_OFF);
        when(shipping.submit(any(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(status(id)).isEqualTo("PROCESSING");
            assertThat(attempts(id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sync_started_at IS NOT NULL FROM shipments WHERE shipment_id=?", Boolean.class, id)).isTrue();
            ShipmentSubmission submission = call.getArgument(0);
            var payload = request(PickupMethod.DROP_OFF, PaymentMethod.CASH);
            assertThat(submission.trackingCode()).isEqualTo(jdbc.queryForObject("SELECT tracking_code FROM shipments WHERE shipment_id=?", String.class, id));
            assertThat(call.<String>getArgument(1)).isEqualTo("shipment:" + submission.trackingCode());
            assertThat(submission.sender()).isEqualTo(new ShipmentSubmission.Contact(payload.sender().name(), payload.sender().phone(), payload.sender().address()));
            assertThat(submission.recipient()).isEqualTo(new ShipmentSubmission.Contact(payload.recipient().name(), payload.recipient().phone(), payload.recipient().address()));
            assertThat(submission.parcel().weightKg()).isEqualByComparingTo(payload.parcel().weightKg());
            assertThat(submission.parcel().lengthCm()).isEqualByComparingTo(payload.parcel().lengthCm());
            assertThat(submission.parcel().widthCm()).isEqualByComparingTo(payload.parcel().widthCm());
            assertThat(submission.parcel().heightCm()).isEqualByComparingTo(payload.parcel().heightCm());
            assertThat(submission.pickupMethod()).isEqualTo("DROP_OFF");
            assertThat(submission.dropOffLocationId()).isEqualTo("101");
            assertThat(submission.note()).isEqualTo(payload.note());
            assertThat(submission.items()).hasSize(2);
            for (int index = 0; index < 2; index++) {
                var item = submission.items().get(index); var expected = payload.items().get(index);
                assertThat(item.name()).isEqualTo(expected.name());
                assertThat(item.category()).isEqualTo(expected.category());
                assertThat(item.quantity()).isEqualTo(expected.quantity());
                assertThat(item.declaredValue()).isEqualByComparingTo(expected.declaredValue());
                assertThat(item.note()).isEqualTo(expected.note());
            }
            return new SubmissionResult(OperationOutcome.CONFIRMED);
        });

        sync.sync(id);

        verify(shipping, times(1)).submit(any(), anyString());
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(attempts(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sync_last_error_code FROM shipments WHERE shipment_id=?", String.class, id)).isNull();
    }

    /** VNPAY without verified payment is never claimed, including an incorrectly assigned PENDING state. */
    @Test void Test_sync_002() {
        long id = seed(PaymentMethod.VNPAY, PickupMethod.HOME_PICKUP);
        sync.sync(id);
        assertThat(status(id)).isEqualTo("WAITING_PAYMENT");
        jdbc.update("UPDATE shipments SET sync_status='PENDING' WHERE shipment_id=?", id);
        sync.sync(id);
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(attempts(id)).isZero();
        verifyNoInteractions(shipping);
    }

    /** Cancellation and cancellation requests always exclude a shipment from a new claim. */
    @Test void Test_sync_003() {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        for (var cancellation : List.of("REQUESTED", "UNKNOWN", "CONFIRMED", "REJECTED")) {
            jdbc.update("UPDATE shipments SET cancellation_status=? WHERE shipment_id=?", cancellation, id);
            sync.sync(id);
            assertThat(status(id)).isEqualTo("PENDING");
            assertThat(attempts(id)).isZero();
        }
        jdbc.update("UPDATE shipments SET cancellation_status='NONE',cancelled_at=CURRENT_TIMESTAMP(6) WHERE shipment_id=?", id);
        sync.sync(id);
        assertThat(attempts(id)).isZero();
        verifyNoInteractions(shipping);
    }

    /** Not-due, non-PENDING and nonexistent shipments cannot trigger submissions. */
    @Test void Test_sync_004() {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        jdbc.update("UPDATE shipments SET sync_next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY) WHERE shipment_id=?", id);
        sync.sync(id);
        for (var value : List.of("WAITING_PAYMENT", "PROCESSING", "SYNCED", "UNKNOWN", "BLOCKED", "CANCELLED")) {
            jdbc.update("UPDATE shipments SET sync_status=?,sync_next_attempt_at=NULL WHERE shipment_id=?", value, id);
            sync.sync(id);
        }
        sync.sync(Long.MAX_VALUE);
        assertThat(attempts(id)).isZero();
        verifyNoInteractions(shipping);
    }

    /** Two workers race for one shipment; the losing worker returns while the sole HTTP call is in flight. */
    @Test void Test_sync_005() throws Exception {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        var start = new CyclicBarrier(2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        when(shipping.submit(any(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new SubmissionResult(OperationOutcome.CONFIRMED);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> worker = () -> { start.await(10, TimeUnit.SECONDS); sync.sync(id); completed.countDown(); return null; };
            var first = executor.submit(worker); var second = executor.submit(worker);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(status(id)).isEqualTo("PROCESSING");
                assertThat(attempts(id)).isEqualTo(1);
                verify(shipping, times(1)).submit(any(), anyString());
            } finally { release.countDown(); }
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        }
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(attempts(id)).isEqualTo(1);
    }

    /** The orchestration boundary rejects a caller transaction before a claim or HTTP call. */
    @Test void Test_sync_006() {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(ignored -> { sync.sync(id); return null; }))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(status(id)).isEqualTo("PENDING");
        assertThat(attempts(id)).isZero();
        verifyNoInteractions(shipping);
    }

    /** Old workers cannot overwrite another attempt, another start time or a reconciled state. */
    @Test void Test_finish_001() {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        var claim = state.tryClaim(id, Instant.now().plusSeconds(1)).orElseThrow();
        jdbc.update("UPDATE shipments SET sync_attempt_count=sync_attempt_count+1 WHERE shipment_id=?", id);
        state.finish(claim, OperationOutcome.CONFIRMED);
        assertThat(status(id)).isEqualTo("PROCESSING");
        jdbc.update("UPDATE shipments SET sync_attempt_count=?,sync_started_at=DATE_ADD(sync_started_at, INTERVAL 1 SECOND) WHERE shipment_id=?", claim.attempt(), id);
        state.finish(claim, OperationOutcome.CONFIRMED);
        assertThat(status(id)).isEqualTo("PROCESSING");
        jdbc.update("UPDATE shipments SET sync_started_at=DATE_SUB(sync_started_at, INTERVAL 1 SECOND),sync_status='UNKNOWN' WHERE shipment_id=?", id);
        state.finish(claim, OperationOutcome.CONFIRMED);
        assertThat(status(id)).isEqualTo("UNKNOWN");
    }

    /** Rejected, uncertain and malformed replies never mark a shipment SYNCED or leave it eligible for resend. */
    @Test void Test_sync_007() {
        for (var outcome : Arrays.asList(OperationOutcome.REJECTED, OperationOutcome.UNKNOWN, null)) {
            long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
            when(shipping.submit(any(), anyString())).thenReturn(new SubmissionResult(outcome));
            sync.sync(id);
            assertThat(status(id)).isEqualTo(outcome == OperationOutcome.REJECTED ? "BLOCKED" : "UNKNOWN");
            assertThat(jdbc.queryForObject("SELECT sync_last_error_code FROM shipments WHERE shipment_id=?", String.class, id))
                    .isEqualTo(outcome == null ? "EXTERNAL_INVALID_RESPONSE" : "SHIPPING_" + outcome.name());
            sync.sync(id);
            assertThat(attempts(id)).isEqualTo(1);
        }
        verify(shipping, times(3)).submit(any(), anyString());
    }

    /** External errors persist only a safe code and cannot cause a blind mutation retry. */
    @Test void Test_sync_008() {
        for (var kind : IntegrationException.Kind.values()) {
            long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
            doThrow(new IntegrationException(kind, true)).when(shipping).submit(any(), anyString());
            assertThatCode(() -> sync.sync(id)).doesNotThrowAnyException();
            assertThat(status(id)).isEqualTo(kind == IntegrationException.Kind.UNAUTHORIZED ? "BLOCKED" : "UNKNOWN");
            assertThat(jdbc.queryForObject("SELECT sync_last_error_code FROM shipments WHERE shipment_id=?", String.class, id))
                    .isEqualTo("EXTERNAL_" + kind.name());
            sync.sync(id);
            assertThat(attempts(id)).isEqualTo(1);
        }
        verify(shipping, times(IntegrationException.Kind.values().length)).submit(any(), anyString());
    }

    /** Candidate selection is bounded and ordered, and excludes unpaid, cancelled, active and not-due work. */
    @Test void Test_candidates_001() {
        long first = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        long second = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        long future = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        long cancelled = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        long unpaid = seed(PaymentMethod.VNPAY, PickupMethod.HOME_PICKUP);
        long processing = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        long noDueDate = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        jdbc.update("UPDATE shipments SET sync_next_attempt_at='2026-01-01 00:00:00' WHERE customer_id=?", customer.userId());
        jdbc.update("UPDATE shipments SET sync_next_attempt_at='2026-01-01 00:00:00.000001' WHERE shipment_id=?", future);
        jdbc.update("UPDATE shipments SET cancellation_status='REQUESTED' WHERE shipment_id=?", cancelled);
        jdbc.update("UPDATE shipments SET sync_status='PENDING' WHERE shipment_id=?", unpaid);
        jdbc.update("UPDATE shipments SET sync_status='PROCESSING' WHERE shipment_id=?", processing);
        jdbc.update("UPDATE shipments SET sync_next_attempt_at=NULL WHERE shipment_id=?", noDueDate);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(state.candidates(now, 2)).containsExactly(noDueDate, first);
        assertThat(state.candidates(now, 100)).containsExactly(noDueDate, first, second);
        verifyNoInteractions(shipping);
    }

    /** A real loopback HTTP adapter sends the saved snapshot only after the claim becomes visible to another connection. */
    @Test void Test_sync_009() throws Exception {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        var body = new AtomicReference<JsonNode>();
        var key = new AtomicReference<String>();
        var observedState = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/dev/shipping/submissions", exchange -> {
            try (exchange) {
                body.set(json.readTree(exchange.getRequestBody()));
                key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                observedState.set(status(id));
                byte[] response = "{\"outcome\":\"CONFIRMED\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var adapter = new ShippingHttpAdapter("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), Duration.ofSeconds(5));
            when(shipping.submit(any(), anyString())).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return adapter.submit(call.getArgument(0), call.getArgument(1));
            });
            sync.sync(id);
            assertThat(observedState.get()).isEqualTo("PROCESSING");
            assertThat(status(id)).isEqualTo("SYNCED");
            assertThat(body.get().get("sender").get("name").asText()).isEqualTo("Sender");
            assertThat(body.get().get("items").size()).isEqualTo(2);
            assertThat(body.get().get("pickupMethod").asText()).isEqualTo("HOME_PICKUP");
            assertThat(body.get().get("dropOffLocationId").isNull()).isTrue();
            assertThat(key.get()).isEqualTo("shipment:" + body.get().get("trackingCode").asText());
            verify(shipping, times(1)).submit(any(), anyString());
        } finally { server.stop(0); }
    }

    /** An absent reply is uncertain and cannot make the shipment eligible for a second submit. */
    @Test void Test_sync_010() {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        when(shipping.submit(any(), anyString())).thenReturn(null);
        sync.sync(id); sync.sync(id);
        assertThat(status(id)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT sync_last_error_code FROM shipments WHERE shipment_id=?", String.class, id))
                .isEqualTo("EXTERNAL_INVALID_RESPONSE");
        verify(shipping, times(1)).submit(any(), anyString());
    }

    /** Guest counter CASH also uses the shared claim flow without requiring a Customer account association. */
    @Test void Test_sync_011() {
        var payload = request(PickupMethod.DROP_OFF, PaymentMethod.CASH);
        long id = writer.createCounter(77, "counter", "service:counter", UUID.randomUUID().toString(),
                "a".repeat(64), payload, payload.confirmedFee()).shipmentId();
        guestIds.add(id);
        when(shipping.submit(any(), anyString())).thenReturn(new SubmissionResult(OperationOutcome.CONFIRMED));
        sync.sync(id);
        assertThat(status(id)).isEqualTo("SYNCED");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM shipments WHERE shipment_id=?", Long.class, id)).isNull();
        verify(shipping, times(1)).submit(any(), anyString());
    }

    /** Duplicate or late success and failure replies cannot replace an already recorded uncertain result. */
    @Test void Test_finish_002() {
        long id = seed(PaymentMethod.CASH, PickupMethod.HOME_PICKUP);
        var claim = state.tryClaim(id, Instant.now().plusSeconds(1)).orElseThrow();
        state.finish(claim, OperationOutcome.UNKNOWN);
        state.finish(claim, OperationOutcome.CONFIRMED);
        state.fail(claim, IntegrationException.Kind.UNAUTHORIZED);
        assertThat(status(id)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT sync_last_error_code FROM shipments WHERE shipment_id=?", String.class, id))
                .isEqualTo("SHIPPING_UNKNOWN");
        assertThat(attempts(id)).isEqualTo(1);
    }
}
