package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.pricing.PricingClient;
import com.dcpost.customer.integration.pricing.dto.FeeQuote;
import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.warehouse.WarehouseClient;
import com.dcpost.customer.integration.warehouse.dto.DropOffLocation;
import com.dcpost.customer.shipment.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.dcpost.customer.integration.common.IntegrationException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.dcpost.customer.shipment.ShipmentCreationServiceTest.request;

@SpringBootTest(properties = {
        "customer.internal.services.counter-a.api-key=isolated-counter-a-key",
        "customer.internal.services.counter-a.permissions=COUNTER_SHIPMENT_CREATE",
        "customer.internal.services.counter-b.api-key=isolated-counter-b-key",
        "customer.internal.services.counter-b.permissions=COUNTER_SHIPMENT_CREATE",
        "customer.internal.services.cashier.api-key=isolated-counter-cashier-key"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
class CounterShipmentIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PricingClient pricing;
    @MockitoBean WarehouseClient warehouses;
    @MockitoBean ShippingClient shipping;
    String key;

    @BeforeEach void setup() {
        key = UUID.randomUUID().toString();
        when(pricing.quote(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new FeeQuote(new BigDecimal("25000"), "VND", Instant.now());
        });
        when(warehouses.getDropOffLocations()).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(new DropOffLocation("101", "Office", "Address"));
        });
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE i FROM shipment_items i JOIN shipments s ON i.shipment_id=s.shipment_id WHERE s.creation_request_key=?", key);
        jdbc.update("DELETE FROM shipments WHERE creation_request_key=?", key);
    }

    Map<String, Object> payload(PickupMethod pickup) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("createdBy", 77);
        payload.put("shipment", request(pickup, PaymentMethod.CASH));
        return payload;
    }

    int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE creation_request_key=?", Integer.class, key);
    }

    /** A trusted counter creates a guest shipment, preserving employee source and atomic items. */
    @Test void Test_createCounter_001() throws Exception {
        long usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        var payload = payload(PickupMethod.DROP_OFF);
        payload.put("createdBySource", "spoofed-service");
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isCreated()).andExpect(jsonPath("shipmentId").isNumber())
                .andExpect(jsonPath("paymentMethod").value("CASH"))
                .andExpect(jsonPath("syncStatus").value("PENDING"));
        var stored = jdbc.queryForMap("SELECT * FROM shipments WHERE creation_request_key=?", key);
        assertThat(stored.get("customer_id")).isNull();
        assertThat(stored.get("created_by")).isEqualTo(77);
        assertThat(stored.get("created_by_source")).isEqualTo("counter-a");
        assertThat(stored.get("creation_channel")).isEqualTo("COUNTER");
        assertThat(stored.get("drop_off_warehouse_id")).isEqualTo(101);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment_items WHERE shipment_id=?", Integer.class,
                stored.get("shipment_id"))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(usersBefore);
        verifyNoInteractions(shipping);
    }

    /** Counter home pickup is rejected before any dependency or database mutation. */
    @Test void Test_createCounter_002() throws Exception {
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload(PickupMethod.HOME_PICKUP))))
                .andExpect(status().isBadRequest());
        assertThat(count()).isZero();
        verifyNoInteractions(pricing, warehouses, shipping);
    }

    /** No customer may be linked until a verifiable group contract exists. */
    @Test void Test_createCounter_003() throws Exception {
        var payload = payload(PickupMethod.DROP_OFF);
        payload.put("customerId", 123);
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        assertThat(count()).isZero();
        verifyNoInteractions(pricing, warehouses, shipping);
    }

    /** Employee IDs must fit positive INT and the request key is mandatory. */
    @Test void Test_createCounter_004() throws Exception {
        for (Object actor : Arrays.asList(null, 0, -1, 2147483648L, 77.5)) {
            var payload = payload(PickupMethod.DROP_OFF);
            payload.put("createdBy", actor);
            mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(payload)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload(PickupMethod.DROP_OFF))))
                .andExpect(status().isBadRequest());
        assertThat(count()).isZero();
        verifyNoInteractions(pricing, warehouses, shipping);
    }

    private long create(String apiKey, Object payload) throws Exception {
        var response = mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", apiKey)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).get("shipmentId").asLong();
    }

    /** Separate authenticated services may safely reuse the same request key. */
    @Test void Test_createCounter_005() throws Exception {
        var payload = payload(PickupMethod.DROP_OFF);
        long first = create("isolated-counter-a-key", payload);
        long second = create("isolated-counter-b-key", payload);
        assertThat(second).isNotEqualTo(first);
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT creation_request_scope FROM shipments WHERE creation_request_key=?", String.class, key))
                .containsExactlyInAnyOrder("service:counter-a", "service:counter-b");
        assertThat(jdbc.queryForObject("SELECT created_by_source FROM shipments WHERE shipment_id=?", String.class, second))
                .isEqualTo("counter-b");
        verifyNoInteractions(shipping);
    }

    /** Counter replay works during outages but changing the employee or payload requires a new key. */
    @Test void Test_createCounter_006() throws Exception {
        var payload = payload(PickupMethod.DROP_OFF);
        long first = create("isolated-counter-a-key", payload);
        com.fasterxml.jackson.databind.node.ObjectNode equivalent = json.valueToTree(payload);
        ((com.fasterxml.jackson.databind.node.ObjectNode) equivalent.path("shipment").path("sender"))
                .put("name", "  Sender  ").put("phone", "+84 900-000-000");
        ((com.fasterxml.jackson.databind.node.ObjectNode) equivalent.path("shipment"))
                .put("note", "  Fragile  ").put("confirmedFee", new BigDecimal("25000.00"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) equivalent.path("shipment").path("parcel"))
                .put("weightKg", new BigDecimal("1.0000"));
        reset(pricing, warehouses);
        when(pricing.quote(any())).thenThrow(new IntegrationException(IntegrationException.Kind.UNAVAILABLE, false));
        assertThat(create("isolated-counter-a-key", equivalent)).isEqualTo(first);
        assertThat(create("isolated-counter-a-key", payload)).isEqualTo(first);
        payload.put("createdBy", 78);
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("IDEMPOTENCY_CONFLICT"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) equivalent.path("shipment")).put("note", "Changed contents");
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-a-key")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(equivalent)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(count()).isEqualTo(1);
        verifyNoInteractions(pricing, warehouses, shipping);
    }

    /** CASH confirmation and counter creation are separate permissions; absent or wrong keys cannot enter. */
    @Test void Test_createCounter_007() throws Exception {
        mvc.perform(post("/api/v1/internal/shipments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "invalid-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/shipments").header("X-API-Key", "isolated-counter-cashier-key"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/internal/shipments/123/cash-confirmations").header("X-API-Key", "isolated-counter-a-key"))
                .andExpect(status().isForbidden());
        assertThat(count()).isZero();
        verifyNoInteractions(pricing, warehouses, shipping);
    }

    /** VNPay counter creation only records the selected method and waits for verified payment. */
    @Test void Test_createCounter_008() throws Exception {
        var payload = payload(PickupMethod.DROP_OFF);
        payload.put("shipment", request(PickupMethod.DROP_OFF, PaymentMethod.VNPAY));
        long id = create("isolated-counter-a-key", payload);
        assertThat(jdbc.queryForObject("SELECT sync_status FROM shipments WHERE shipment_id=?", String.class, id))
                .isEqualTo("WAITING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT sync_next_attempt_at FROM shipments WHERE shipment_id=?", Object.class, id))
                .isNull();
        verifyNoInteractions(shipping);
    }

    /** Concurrent counter requests with one service key commit one guest shipment with unmixed items. */
    @Test void Test_createCounter_009() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(pricing.quote(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            barrier.await(10, TimeUnit.SECONDS);
            return new FeeQuote(new BigDecimal("25000"), "VND", Instant.now());
        });
        var payload = payload(PickupMethod.DROP_OFF);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> create("isolated-counter-a-key", payload));
            var second = executor.submit(() -> create("isolated-counter-a-key", payload));
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment_items i JOIN shipments s ON i.shipment_id=s.shipment_id WHERE s.creation_request_key=?", Integer.class, key))
                .isEqualTo(2);
        verifyNoInteractions(shipping);
    }
}
