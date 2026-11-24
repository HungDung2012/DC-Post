package com.dcpost.customer.shipment;

import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShipmentPersistenceIT {
    @Autowired EntityManager entityManager;
    private final Instant now = Instant.parse("2026-11-24T00:00:00Z");

    private Shipment shipment(Long customerId, PaymentMethod payment, PickupMethod pickup, CreationChannel channel) {
        return shipment(customerId, payment, pickup, channel, new BigDecimal("25000"));
    }

    private Shipment shipment(Long customerId, PaymentMethod payment, PickupMethod pickup, CreationChannel channel, BigDecimal fee) {
        return new Shipment(customerId, "DC" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                new Shipment.Contact("Original sender", "0900000000", "Original origin"),
                new Shipment.Contact("Original recipient", "0911111111", "Original destination"),
                new Shipment.Parcel(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                fee, pickup, channel, payment, pickup == PickupMethod.DROP_OFF ? 101 : null,
                new Shipment.Creation("test:persistence", UUID.randomUUID().toString(), "0".repeat(64),
                        channel == CreationChannel.ONLINE ? "customer" : "counter", 1), null, now);
    }

    /** Persisting a shipment cascades its items and preserves sender/recipient snapshots. */
    @Test
    void Test_persist_001() {
        var shipment = shipment(null, PaymentMethod.CASH, PickupMethod.DROP_OFF, CreationChannel.COUNTER);
        shipment.addItem("Book", "Books", 2, new BigDecimal("100000"), "Handle gently");
        assertThat(shipment.getSenderName()).isEqualTo("Original sender");
        entityManager.persist(shipment); entityManager.flush(); entityManager.clear();
        var stored = entityManager.find(Shipment.class, shipment.getId());
        assertThat(stored.getSenderName()).isEqualTo("Original sender");
        assertThat(stored.getRecipientAddress()).isEqualTo("Original destination");
        assertThat(stored.getShippingFee()).isEqualByComparingTo("25000");
        assertThat(stored.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(stored.getItems()).hasSize(1);
        assertThat(stored.getItems().getFirst().getName()).isEqualTo("Book");
    }

    /** VNPay shipments remain waiting for payment in storage before scheduler eligibility. */
    @Test
    void Test_persist_002() {
        var shipment = shipment(null, PaymentMethod.VNPAY, PickupMethod.DROP_OFF, CreationChannel.COUNTER);
        assertThat(shipment.getSyncStatus()).isEqualTo(SyncStatus.WAITING_PAYMENT);
        entityManager.persist(shipment); entityManager.flush(); entityManager.clear();
        assertThat(entityManager.find(Shipment.class, shipment.getId()).getSyncStatus()).isEqualTo(SyncStatus.WAITING_PAYMENT);
    }

    /** ONLINE home pickup accepts a local customer and changing the profile leaves shipment snapshots intact. */
    @Test
    void Test_persist_003() {
        var customer = new User("Customer", "shipment-persistence@example.invalid", "0922222222", "fixture-hash", now);
        entityManager.persist(customer); entityManager.flush();
        var shipment = shipment(customer.getId(), PaymentMethod.CASH, PickupMethod.HOME_PICKUP, CreationChannel.ONLINE);
        assertThat(shipment.getSenderName()).isEqualTo("Original sender");
        entityManager.persist(shipment);
        customer.updateProfile("Changed name", "0922222222", "Changed address", now);
        entityManager.flush(); entityManager.clear();
        assertThat(entityManager.find(Shipment.class, shipment.getId()).getSenderName()).isEqualTo("Original sender");
    }

    /** Reject fractional VND before DECIMAL(15,2) can round away a tiny fraction. */
    @Test
    void Test_create_001() {
        assertThatThrownBy(() -> shipment(null, PaymentMethod.CASH, PickupMethod.DROP_OFF,
                CreationChannel.COUNTER, new BigDecimal("25000.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
