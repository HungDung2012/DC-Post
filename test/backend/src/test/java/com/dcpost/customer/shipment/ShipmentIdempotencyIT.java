package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.integration.pricing.PricingClient;
import com.dcpost.customer.integration.pricing.dto.FeeQuote;
import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.warehouse.WarehouseClient;
import com.dcpost.customer.integration.warehouse.dto.DropOffLocation;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.dto.CreateShipmentRequest.Item;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.service.*;
import com.dcpost.customer.user.entity.User;
import com.dcpost.customer.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.dcpost.customer.shipment.ShipmentCreationServiceTest.request;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentIdempotencyIT {
    @Autowired ShipmentCreationService service;
    @Autowired ShipmentWriteService writer;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PricingClient pricing;
    @MockitoBean WarehouseClient warehouses;
    @MockitoBean ShippingClient shipping;
    @MockitoBean TrackingCodeGenerator codes;
    CustomerPrincipal customer;
    String key;

    @BeforeEach void setup() {
        var user = users.saveAndFlush(new User("Shipment IT", UUID.randomUUID() + "@example.invalid",
                "0" + String.format("%010d", ThreadLocalRandom.current().nextLong(10000000000L)), "hash", Instant.now()));
        customer = new CustomerPrincipal(user.getId(), 0);
        key = UUID.randomUUID().toString();
        when(codes.generate()).thenAnswer(call -> "DC" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
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
        jdbc.update("DELETE i FROM shipment_items i JOIN shipments s ON s.shipment_id=i.shipment_id WHERE s.customer_id=?", customer.userId());
        jdbc.update("DELETE FROM shipments WHERE customer_id=?", customer.userId());
        users.deleteById(customer.userId());
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE "
                + (table.equals("shipments") ? "customer_id=?" : "shipment_id IN (SELECT shipment_id FROM shipments WHERE customer_id=?)"),
                Integer.class, customer.userId());
    }

    /** Committed VNPay snapshots replay while dependencies are down, without shipping submission. */
    @Test void Test_create_001() {
        var payload = request(PickupMethod.DROP_OFF, PaymentMethod.VNPAY);
        var first = service.create(customer, key, payload);
        assertThat(first.shipmentId()).isPositive();
        assertThat(first.syncStatus()).isEqualTo(SyncStatus.WAITING_PAYMENT);
        reset(pricing, warehouses);
        when(pricing.quote(any())).thenThrow(new IntegrationException(IntegrationException.Kind.UNAVAILABLE, false));
        var replay = service.create(customer, key, payload);
        assertThat(replay.shipmentId()).isEqualTo(first.shipmentId());
        assertThat(replay.shippingFee()).isEqualByComparingTo(first.shippingFee());
        assertThat(count("shipments")).isEqualTo(1);
        assertThat(count("shipment_items")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sender_name FROM shipments WHERE shipment_id=?", String.class, first.shipmentId()))
                .isEqualTo("Sender");
        verifyNoInteractions(pricing, warehouses, shipping);
    }

    /** Concurrent identical keys leave one committed shipment and one complete set of items. */
    @Test void Test_create_002() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(pricing.quote(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            barrier.await(10, TimeUnit.SECONDS);
            return new FeeQuote(new BigDecimal("25000"), "VND", Instant.now());
        });
        var payload = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(customer, key, payload));
            var second = executor.submit(() -> service.create(customer, key, payload));
            assertThat(first.get(20, TimeUnit.SECONDS).shipmentId()).isEqualTo(second.get(20, TimeUnit.SECONDS).shipmentId());
        }
        assertThat(count("shipments")).isEqualTo(1);
        assertThat(count("shipment_items")).isEqualTo(2);
        verifyNoInteractions(shipping);
    }

    /** A database failure on the second item rolls back the shipment and first item together. */
    @Test void Test_create_003() {
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        var invalid = new CreateShipmentRequest(base.sender(), base.recipient(), base.parcel(),
                List.of(base.items().getFirst(), new Item("Invalid", "Test", 0, BigDecimal.ONE, null)),
                base.pickupMethod(), null, base.paymentMethod(), base.confirmedFee(), null);
        assertThatThrownBy(() -> writer.create(customer.userId(), Math.toIntExact(customer.userId()),
                "customer:" + customer.userId(), key, "a".repeat(64), invalid, base.confirmedFee()))
                .isInstanceOf(org.springframework.orm.jpa.JpaSystemException.class)
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(count("shipments")).isZero();
        assertThat(count("shipment_items")).isZero();
    }

    /** Real MySQL tracking uniqueness failure rolls back, then retries in a fresh transaction. */
    @Test void Test_create_004() {
        var payload = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        var first = service.create(customer, key, payload);
        var nextCode = "DC" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        when(codes.generate()).thenReturn(first.trackingCode(), nextCode);
        var second = service.create(customer, key + "-second", payload);
        assertThat(second.trackingCode()).isEqualTo(nextCode);
        assertThat(count("shipments")).isEqualTo(2);
        assertThat(count("shipment_items")).isEqualTo(4);
    }

    /** The orchestration boundary rejects a transaction opened by a caller before external calls. */
    @Test void Test_create_005() {
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.execute(status -> service.create(customer, key,
                request(PickupMethod.DROP_OFF, PaymentMethod.CASH))))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        verifyNoInteractions(pricing, warehouses, shipping);
        assertThat(count("shipments")).isZero();
    }

    /** Competing payloads under one key yield exactly one success and one payload conflict. */
    @Test void Test_create_006() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(pricing.quote(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            barrier.await(10, TimeUnit.SECONDS);
            return new FeeQuote(new BigDecimal("25000"), "VND", Instant.now());
        });
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        var changed = new CreateShipmentRequest(base.sender(), base.recipient(), base.parcel(), base.items(),
                base.pickupMethod(), null, base.paymentMethod(), base.confirmedFee(), "Another note");
        var outcomes = new ArrayList<Object>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(customer, key, base));
            var second = executor.submit(() -> service.create(customer, key, changed));
            for (var future : List.of(first, second)) {
                try { outcomes.add(future.get(20, TimeUnit.SECONDS)); }
                catch (ExecutionException exception) { outcomes.add(exception.getCause()); }
            }
        }
        assertThat(outcomes.stream().filter(ShipmentCreated.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(ShipmentCreationException.class::isInstance)
                .map(ShipmentCreationException.class::cast).map(ShipmentCreationException::code).toList())
                .containsExactly(ShipmentCreationException.Code.IDEMPOTENCY_CONFLICT);
        assertThat(count("shipments")).isEqualTo(1);
        assertThat(count("shipment_items")).isEqualTo(2);
    }
}
