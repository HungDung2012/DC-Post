package com.dcpost.customer.shipment;

import com.dcpost.customer.auth.dto.*;
import com.dcpost.customer.auth.service.*;
import com.dcpost.customer.integration.pricing.PricingClient;
import com.dcpost.customer.integration.pricing.dto.FeeQuote;
import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.warehouse.WarehouseClient;
import com.dcpost.customer.integration.shipping.dto.ShipmentTracking;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.service.ShipmentWriteService;
import com.dcpost.customer.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
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

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
class ShipmentOwnershipIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegistrationService registration;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired ShipmentWriteService writer;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ShippingClient shipping;
    @MockitoBean PricingClient pricing;
    @MockitoBean WarehouseClient warehouses;
    AuthSession owner;
    AuthSession other;
    long cashId;
    long vnpayId;
    long foreignId;

    AuthSession customer() {
        var email = UUID.randomUUID() + "@example.invalid";
        var phone = "09" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000));
        registration.register(new RegisterRequest("Shipment owner", email, phone, "safe-password"));
        return auth.login(email, "safe-password");
    }

    long seed(AuthSession session, PaymentMethod payment, String recipient) {
        var base = request(PickupMethod.HOME_PICKUP, payment);
        var payload = new CreateShipmentRequest(base.sender(),
                new CreateShipmentRequest.Contact(recipient, "0911111111", "Destination"), base.parcel(),
                base.items(), base.pickupMethod(), null, payment, base.confirmedFee(), base.note());
        long id = session.account().id();
        return writer.create(id, Math.toIntExact(id), "customer:" + id, UUID.randomUUID().toString(),
                "a".repeat(64), payload, base.confirmedFee()).shipmentId();
    }

    @BeforeEach void setup() {
        owner = customer(); other = customer();
        cashId = seed(owner, PaymentMethod.CASH, "Alice One");
        vnpayId = seed(owner, PaymentMethod.VNPAY, "Bob100%_");
        foreignId = seed(other, PaymentMethod.CASH, "Alice Foreign");
        jdbc.update("UPDATE shipments SET created_at='2026-11-24 00:00:00' WHERE customer_id IN (?,?)",
                owner.account().id(), other.account().id());
        when(pricing.quote(any())).thenReturn(new FeeQuote(new BigDecimal("25000"), "VND", Instant.now()));
    }

    @AfterEach void cleanup() {
        for (var session : List.of(owner, other)) {
            jdbc.update("DELETE i FROM shipment_items i JOIN shipments s ON i.shipment_id=s.shipment_id WHERE s.customer_id=?", session.account().id());
            jdbc.update("DELETE FROM shipments WHERE customer_id=?", session.account().id());
            users.deleteById(session.account().id());
        }
    }

    String bearer() { return "Bearer " + owner.accessToken(); }

    /** List count, content and deterministic sorting are all restricted to the logged-in customer. */
    @Test void Test_list_001() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
                .andExpect(jsonPath("content.length()").value(2))
                .andExpect(jsonPath("content[0].shipmentId").value(vnpayId))
                .andExpect(jsonPath("content[1].shipmentId").value(cashId));
        verifyNoInteractions(shipping);
    }

    /** Other-customer and nonexistent resource IDs return the same safe not-found representation. */
    @Test void Test_detail_001() throws Exception {
        for (long id : List.of(foreignId, Long.MAX_VALUE))
            mvc.perform(get("/api/v1/customer/shipments/" + id).header("Authorization", bearer()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("SHIPMENT_NOT_FOUND"))
                    .andExpect(jsonPath("correlationId").isNotEmpty());
        verifyNoInteractions(shipping);
    }

    /** Recipient search cannot escape the ownership predicate. */
    @Test void Test_list_002() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments").param("q", "Alice").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].shipmentId").value(cashId));
        verifyNoInteractions(shipping);
    }

    /** Stable ID ordering resolves equal creation timestamps across separate pages. */
    @Test void Test_list_003() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments").param("size", "1").param("page", "0").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
                .andExpect(jsonPath("totalPages").value(2)).andExpect(jsonPath("content[0].shipmentId").value(vnpayId));
        mvc.perform(get("/api/v1/customer/shipments").param("size", "1").param("page", "1").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].shipmentId").value(cashId));
    }

    /** Online creation ignores caller-supplied ownership and actor metadata in favor of the principal. */
    @Test void Test_create_001() throws Exception {
        ObjectNode payload = json.valueToTree(request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH));
        payload.put("customerId", other.account().id()).put("createdBy", 77).put("creationChannel", "COUNTER");
        var response = mvc.perform(post("/api/v1/customer/shipments").header("Authorization", bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(response.getContentAsByteArray()).get("shipmentId").asLong();
        var stored = jdbc.queryForMap("SELECT customer_id,created_by,created_by_source,creation_channel FROM shipments WHERE shipment_id=?", id);
        assertThat(stored.get("customer_id")).isEqualTo(owner.account().id());
        assertThat(stored.get("created_by")).isEqualTo(Math.toIntExact(owner.account().id()));
        assertThat(stored.get("created_by_source")).isEqualTo("customer");
        assertThat(stored.get("creation_channel")).isEqualTo("ONLINE");
        verifyNoInteractions(shipping);
    }

    /** Owned detail exposes snapshots and items without scheduler internals or a premature Shipping call. */
    @Test void Test_detail_002() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments/" + cashId).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("shipment.shipmentId").value(cashId))
                .andExpect(jsonPath("shipment.sender.name").value("Sender"))
                .andExpect(jsonPath("shipment.recipient.name").value("Alice One"))
                .andExpect(jsonPath("shipment.items.length()").value(2))
                .andExpect(jsonPath("shipment.shippingFee").value(25000))
                .andExpect(jsonPath("shipment.syncStatus").value("PENDING"))
                .andExpect(jsonPath("shipment.creationRequestHash").doesNotExist())
                .andExpect(jsonPath("shipment.creationRequestKey").doesNotExist())
                .andExpect(jsonPath("tracking.availability").value("NOT_READY"))
                .andExpect(jsonPath("tracking.sourceStatus").isEmpty());
        verifyNoInteractions(shipping);
    }

    void synced(long id) { jdbc.update("UPDATE shipments SET sync_status='SYNCED' WHERE shipment_id=?", id); }

    String code(long id) { return jdbc.queryForObject("SELECT tracking_code FROM shipments WHERE shipment_id=?", String.class, id); }

    /** Shipping enrichment runs after local reading has finished and never overwrites local recipient data. */
    @Test void Test_detail_003() throws Exception {
        synced(cashId);
        String code = code(cashId);
        when(shipping.getTracking(code)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ShipmentTracking(code, "SOURCE_CUSTOM", Instant.parse("2026-11-26T00:00:00Z"),
                    List.of(new ShipmentTracking.PublicEvent("SOURCE_CUSTOM", Instant.parse("2026-11-26T00:00:00Z"), "Area")),
                    new ShipmentTracking.PrivateDetails("External recipient", "external-phone", "external-address"));
        });
        mvc.perform(get("/api/v1/customer/shipments/" + cashId).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("shipment.recipient.name").value("Alice One"))
                .andExpect(jsonPath("tracking.availability").value("AVAILABLE"))
                .andExpect(jsonPath("tracking.sourceStatus").value("SOURCE_CUSTOM"))
                .andExpect(jsonPath("tracking.events[0].area").value("Area"))
                .andExpect(jsonPath("tracking.privateDetails").doesNotExist());
        verify(shipping).getTracking(code);
    }

    /** Dependency failure preserves the readable local record and marks only enrichment unavailable. */
    @Test void Test_detail_004() throws Exception {
        synced(cashId);
        when(shipping.getTracking(code(cashId)))
                .thenThrow(new IntegrationException(IntegrationException.Kind.UNAVAILABLE, false));
        mvc.perform(get("/api/v1/customer/shipments/" + cashId).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("shipment.items.length()").value(2))
                .andExpect(jsonPath("shipment.shippingFee").value(25000))
                .andExpect(jsonPath("tracking.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("tracking.sourceStatus").isEmpty())
                .andExpect(jsonPath("tracking.errorCode").value("EXTERNAL_UNAVAILABLE"));
    }

    /** Literal LIKE wildcards, tracking codes and recipient phones are searched only within ownership. */
    @Test void Test_list_004() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments").param("q", "%_").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].shipmentId").value(vnpayId));
        mvc.perform(get("/api/v1/customer/shipments").param("q", code(cashId)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].shipmentId").value(cashId))
                .andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get("/api/v1/customer/shipments").param("q", "0911111111").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        verifyNoInteractions(shipping);
    }

    /** Local filters run before pagination and capabilities disclose unsupported global status filters. */
    @Test void Test_list_005() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments").param("paymentMethod", "CASH")
                        .param("createdFrom", "2026-11-24T00:00:00Z").param("createdBefore", "2026-11-25T00:00:00Z")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].shipmentId").value(cashId))
                .andExpect(jsonPath("capabilities.paymentMethodFilter").value(true))
                .andExpect(jsonPath("capabilities.shippingStatusFilter").value(false))
                .andExpect(jsonPath("capabilities.paymentStatusFilter").value(false));
        mvc.perform(get("/api/v1/customer/shipments").param("syncStatus", "WAITING_PAYMENT")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].shipmentId").value(vnpayId));
        mvc.perform(get("/api/v1/customer/shipments").param("createdBefore", "2026-11-24T00:00:00Z")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        verifyNoInteractions(shipping);
    }

    /** Invalid pagination, oversized search, sort overrides and reversed dates fail as client errors. */
    @Test void Test_list_006() throws Exception {
        for (var parameter : List.of(new String[]{"page", "-1"}, new String[]{"page", "2147483647"},
                new String[]{"size", "0"}, new String[]{"size", "101"}, new String[]{"q", "a".repeat(501)},
                new String[]{"sort", "customerId,asc"}))
            mvc.perform(get("/api/v1/customer/shipments").param(parameter[0], parameter[1]).header("Authorization", bearer()))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/customer/shipments").param("createdFrom", "2026-11-25T00:00:00Z")
                        .param("createdBefore", "2026-11-24T00:00:00Z").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(shipping);
    }

    /** Unavailable status filters are rejected explicitly rather than silently returning unfiltered data. */
    @Test void Test_list_007() throws Exception {
        for (var filter : List.of("shippingStatus", "paymentStatus"))
            mvc.perform(get("/api/v1/customer/shipments").param(filter, "PAID").header("Authorization", bearer()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("FILTER_UNAVAILABLE"));
        verifyNoInteractions(shipping);
    }

    /** A customerId query parameter cannot select another customer's records. */
    @Test void Test_list_008() throws Exception {
        mvc.perform(get("/api/v1/customer/shipments").param("customerId", Long.toString(other.account().id()))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
                .andExpect(jsonPath("content[0].shipmentId").value(vnpayId));
    }
}
