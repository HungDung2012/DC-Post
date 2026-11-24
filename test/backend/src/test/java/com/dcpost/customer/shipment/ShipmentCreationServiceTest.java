package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.pricing.PricingClient;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.integration.pricing.dto.FeeQuote;
import com.dcpost.customer.integration.warehouse.WarehouseClient;
import com.dcpost.customer.integration.warehouse.dto.DropOffLocation;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.dto.CreateShipmentRequest;
import com.dcpost.customer.shipment.dto.CreateShipmentRequest.*;
import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.repository.ShipmentRepository;
import com.dcpost.customer.shipment.service.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentCreationServiceTest {
    static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");
    static final CustomerPrincipal CUSTOMER = new CustomerPrincipal(1, 0);
    static final BigDecimal FEE = new BigDecimal("25000");
    static ValidatorFactory validation;
    ShipmentRepository shipments;
    PricingClient pricing;
    WarehouseClient warehouses;
    TrackingCodeGenerator codes;
    ShipmentCreationService service;

    @BeforeAll static void openValidation() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidation() { validation.close(); }
    @BeforeEach void setup() {
        shipments = mock(ShipmentRepository.class);
        pricing = mock(PricingClient.class);
        warehouses = mock(WarehouseClient.class);
        codes = mock(TrackingCodeGenerator.class);
        when(codes.generate()).thenReturn("DC00000000000000000000000000000001");
        when(shipments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(pricing.quote(any())).thenReturn(new FeeQuote(FEE, "VND", NOW));
        when(warehouses.getDropOffLocations()).thenReturn(List.of(new DropOffLocation("101", "Office", "Address")));
        service = new ShipmentCreationService(shipments,
                new ShipmentWriteService(shipments, codes, Clock.fixed(NOW, ZoneOffset.UTC)),
                pricing, warehouses, validation.getValidator());
    }

    static CreateShipmentRequest request(PickupMethod pickup, PaymentMethod payment) {
        return new CreateShipmentRequest(new Contact(" Sender ", "+84 900-000-000", " Origin "),
                new Contact("Recipient", "0911111111", "Destination"),
                new Parcel(new BigDecimal("1.000"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                List.of(new Item(" Book ", "Books", 2, new BigDecimal("100000"), null),
                        new Item("Pen", "Stationery", 1, new BigDecimal("5000"), " Blue ")),
                pickup, pickup == PickupMethod.DROP_OFF ? 101 : null, payment, FEE, " Fragile ");
    }

    /** Online CASH saves normalized snapshots and all items with a pending sync task. */
    @Test void Test_create_001() {
        var result = service.create(CUSTOMER, "request-1", request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH));
        assertThat(result).isNotNull();
        assertThat(result.shippingFee()).isEqualByComparingTo(FEE);
        assertThat(result.syncStatus()).isEqualTo(SyncStatus.PENDING);
        var capture = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).saveAndFlush(capture.capture());
        var saved = capture.getValue();
        assertThat(saved.getSenderName()).isEqualTo("Sender");
        assertThat(saved.getSenderPhone()).isEqualTo("0900000000");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems().getFirst().getName()).isEqualTo("Book");
        assertThat(saved.getCreatedBy()).isEqualTo(1);
        assertThat(saved.getCustomerId()).isEqualTo(1);
        assertThat(saved.getCreationChannel()).isEqualTo(CreationChannel.ONLINE);
        assertThat(saved.getSyncNextAttemptAt()).isEqualTo(NOW);
        verifyNoInteractions(warehouses);
    }

    /** DROP_OFF requires a location provided by Warehouse and VNPay waits for payment. */
    @Test void Test_create_002() {
        var result = service.create(CUSTOMER, "request-2", request(PickupMethod.DROP_OFF, PaymentMethod.VNPAY));
        assertThat(result.syncStatus()).isEqualTo(SyncStatus.WAITING_PAYMENT);
        var capture = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getDropOffWarehouseId()).isEqualTo(101);
        assertThat(capture.getValue().getSyncNextAttemptAt()).isNull();
        verify(warehouses).getDropOffLocations();
    }

    /** A missing or unsupported drop-off location never creates a shipment. */
    @Test void Test_create_003() {
        var base = request(PickupMethod.DROP_OFF, PaymentMethod.CASH);
        var missing = new CreateShipmentRequest(base.sender(), base.recipient(), base.parcel(), base.items(),
                base.pickupMethod(), null, base.paymentMethod(), FEE, null);
        assertThatThrownBy(() -> service.create(CUSTOMER, "missing", missing))
                .isInstanceOf(ShipmentCreationException.class);
        when(warehouses.getDropOffLocations()).thenReturn(List.of(new DropOffLocation("uuid-office", "Other", "Address")));
        assertThatThrownBy(() -> service.create(CUSTOMER, "unknown", base))
                .isInstanceOf(ShipmentCreationException.class);
        verify(shipments, never()).saveAndFlush(any());
    }

    /** Changed backend pricing requires explicit reconfirmation and persists nothing. */
    @Test void Test_create_004() {
        when(pricing.quote(any())).thenReturn(new FeeQuote(new BigDecimal("30000"), "VND", NOW));
        assertThatThrownBy(() -> service.create(CUSTOMER, "changed", request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH)))
                .isInstanceOfSatisfying(ShipmentCreationException.class, error -> {
                    assertThat(error.code()).isEqualTo(ShipmentCreationException.Code.FEE_CHANGED);
                    assertThat(error.currentFee()).isEqualByComparingTo("30000");
                });
        verify(shipments, never()).saveAndFlush(any());
    }

    /** Unavailable Pricing is propagated without a locally invented fee or persisted shipment. */
    @Test void Test_create_005() {
        var failure = new IntegrationException(IntegrationException.Kind.UNAVAILABLE, false);
        when(pricing.quote(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.create(CUSTOMER, "unavailable", request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH)))
                .isSameAs(failure);
        verify(shipments, never()).saveAndFlush(any());
    }

    /** Invalid parcel precision, missing items, invalid phones and fractional fees fail before dependencies. */
    @Test void Test_create_006() {
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        var invalid = List.of(
                new CreateShipmentRequest(base.sender(), base.recipient(),
                        new Parcel(new BigDecimal("0.0001"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                        base.items(), base.pickupMethod(), null, base.paymentMethod(), FEE, null),
                new CreateShipmentRequest(base.sender(), base.recipient(), base.parcel(), List.of(),
                        base.pickupMethod(), null, base.paymentMethod(), FEE, null),
                new CreateShipmentRequest(new Contact("Sender", "invalid", "Origin"), base.recipient(), base.parcel(),
                        base.items(), base.pickupMethod(), null, base.paymentMethod(), FEE, null),
                new CreateShipmentRequest(base.sender(), base.recipient(), base.parcel(), base.items(),
                        base.pickupMethod(), null, base.paymentMethod(), new BigDecimal("25000.001"), null));
        for (var request : invalid)
            assertThatThrownBy(() -> service.create(CUSTOMER, "invalid", request))
                    .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        verifyNoInteractions(pricing, warehouses);
        verify(shipments, never()).saveAndFlush(any());
    }

    /** Request keys are bounded ASCII and local actor IDs cannot overflow provisional INT. */
    @Test void Test_create_007() {
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        for (var key : List.of("", " ", "a".repeat(129), "khóa"))
            assertThatThrownBy(() -> service.create(CUSTOMER, key, base)).isInstanceOf(ShipmentCreationException.class);
        assertThatThrownBy(() -> service.create(new CustomerPrincipal((long) Integer.MAX_VALUE + 1, 0), "actor", base))
                .isInstanceOf(ShipmentCreationException.class);
        verifyNoInteractions(pricing, warehouses);
        verify(shipments, never()).saveAndFlush(any());
    }

    private Shipment saved() {
        var capture = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).saveAndFlush(capture.capture());
        return capture.getValue();
    }

    /** Equivalent normalized payloads replay before calling unavailable external dependencies. */
    @Test void Test_create_008() {
        var base = request(PickupMethod.DROP_OFF, PaymentMethod.CASH);
        var first = service.create(CUSTOMER, "replay", base);
        var stored = saved();
        when(shipments.findByCreationRequestScopeAndCreationRequestKey("customer:1", "replay"))
                .thenReturn(Optional.of(stored));
        reset(pricing, warehouses);
        var equivalent = new CreateShipmentRequest(new Contact("Sender", "0900000000", "Origin"),
                base.recipient(), new Parcel(BigDecimal.ONE, new BigDecimal("10.00"), BigDecimal.TEN, BigDecimal.TEN),
                List.of(new Item("Book", "Books", 2, new BigDecimal("100000.00"), " "), base.items().get(1)),
                base.pickupMethod(), 101, base.paymentMethod(), new BigDecimal("25000.00"), "Fragile");
        assertThatCode(() -> assertThat(service.create(CUSTOMER, "replay", equivalent)).isEqualTo(first))
                .doesNotThrowAnyException();
        verifyNoInteractions(pricing, warehouses);
        verify(shipments, times(1)).saveAndFlush(any());
        assertThat(stored.getCreationRequestHash()).matches("[a-f0-9]{64}").isNotEqualTo("0".repeat(64));
    }

    /** A changed business payload with an existing key is rejected rather than silently replayed. */
    @Test void Test_create_009() {
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        service.create(CUSTOMER, "conflict", base);
        var stored = saved();
        when(shipments.findByCreationRequestScopeAndCreationRequestKey("customer:1", "conflict"))
                .thenReturn(Optional.of(stored));
        var changed = new CreateShipmentRequest(base.sender(), base.recipient(), base.parcel(), base.items(),
                base.pickupMethod(), null, base.paymentMethod(), FEE, "Changed note");
        clearInvocations(pricing);
        assertThatThrownBy(() -> service.create(CUSTOMER, "conflict", changed))
                .isInstanceOfSatisfying(ShipmentCreationException.class,
                        error -> assertThat(error.code()).isEqualTo(ShipmentCreationException.Code.IDEMPOTENCY_CONFLICT));
        verifyNoInteractions(pricing);
        verify(shipments, times(1)).saveAndFlush(any());
    }

    private DataIntegrityViolationException duplicate(String constraint) {
        return new DataIntegrityViolationException("Duplicate", new SQLException(
                "Duplicate entry for key 'shipments." + constraint + "'", "23000", 1062));
    }

    /** A tracking collision retries with a new code while Pricing is verified only once. */
    @Test void Test_create_010() {
        when(shipments.saveAndFlush(any())).thenThrow(duplicate("uq_shipments_tracking"))
                .thenAnswer(call -> call.getArgument(0));
        when(codes.generate()).thenReturn("DC-COLLISION", "DC-NEW");
        assertThatCode(() -> assertThat(service.create(CUSTOMER, "tracking",
                request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH)).trackingCode()).isEqualTo("DC-NEW"))
                .doesNotThrowAnyException();
        verify(pricing, times(1)).quote(any());
        verify(shipments, times(2)).saveAndFlush(any());
    }

    /** Persistent tracking collisions stop after three attempts without retrying arbitrary integrity errors. */
    @Test void Test_create_011() {
        when(shipments.saveAndFlush(any())).thenThrow(duplicate("uq_shipments_tracking"));
        assertThatThrownBy(() -> service.create(CUSTOMER, "tracking", request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH)))
                .isInstanceOfSatisfying(ShipmentCreationException.class,
                        error -> assertThat(error.code()).isEqualTo(ShipmentCreationException.Code.TRACKING_UNAVAILABLE));
        verify(shipments, times(3)).saveAndFlush(any());
        reset(shipments);
        var failure = new DataIntegrityViolationException("Foreign key failed");
        when(shipments.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.create(CUSTOMER, "other-error", request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH)))
                .isSameAs(failure);
        verify(shipments, times(1)).saveAndFlush(any());
    }

    /** An idempotency race reads the winner after the losing write has failed. */
    @Test void Test_create_012() {
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        var first = service.create(CUSTOMER, "race", base);
        var winner = saved();
        reset(shipments);
        when(shipments.findByCreationRequestScopeAndCreationRequestKey("customer:1", "race"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(shipments.saveAndFlush(any())).thenThrow(duplicate("uq_shipments_creation_request"));
        assertThatCode(() -> assertThat(service.create(CUSTOMER, "race", base)).isEqualTo(first))
                .doesNotThrowAnyException();
        verify(shipments, times(1)).saveAndFlush(any());
    }

    /** Invalid dependency money must never be returned as a valid reconfirmable VND fee. */
    @Test void Test_create_013() {
        for (var quote : List.of(new FeeQuote(new BigDecimal("25000.5"), "VND", NOW),
                new FeeQuote(new BigDecimal("-1"), "VND", NOW),
                new FeeQuote(FEE, "USD", NOW), new FeeQuote(new BigDecimal("10000000000000"), "VND", NOW))) {
            when(pricing.quote(any())).thenReturn(quote);
            assertThatThrownBy(() -> service.create(CUSTOMER, "invalid-quote", request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH)))
                    .isInstanceOfSatisfying(IntegrationException.class,
                            error -> assertThat(error.kind()).isEqualTo(IntegrationException.Kind.INVALID_RESPONSE));
        }
        verify(shipments, never()).saveAndFlush(any());
    }

    /** Replay refuses a record owned by another customer even if a repository scope is inconsistent. */
    @Test void Test_create_014() {
        var base = request(PickupMethod.HOME_PICKUP, PaymentMethod.CASH);
        service.create(new CustomerPrincipal(2, 0), "ownership", base);
        var other = saved();
        when(shipments.findByCreationRequestScopeAndCreationRequestKey("customer:1", "ownership"))
                .thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.create(CUSTOMER, "ownership", base))
                .isInstanceOfSatisfying(ShipmentCreationException.class,
                        error -> assertThat(error.code()).isEqualTo(ShipmentCreationException.Code.IDEMPOTENCY_CONFLICT));
        verify(shipments, times(1)).saveAndFlush(any());
    }
}
