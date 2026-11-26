package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.shipping.dto.ShipmentTracking;
import com.dcpost.customer.security.CustomerPrincipal;
import com.dcpost.customer.shipment.dto.*;
import com.dcpost.customer.shipment.dto.ShipmentDetailResponse.*;
import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.*;
import com.dcpost.customer.shipment.service.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentQueryServiceTest {
    final CustomerPrincipal customer = new CustomerPrincipal(1, 0);
    final Instant now = Instant.parse("2026-11-26T00:00:00Z");
    ShipmentReadService reader;
    ShippingClient shipping;
    ShipmentQueryService service;
    Snapshot local;

    @BeforeEach void setup() {
        reader = mock(ShipmentReadService.class);
        shipping = mock(ShippingClient.class);
        service = new ShipmentQueryService(reader, shipping);
        var entity = new Shipment(1L, "DC-OWNED", new Shipment.Contact("Sender", "0900000000", "Origin"),
                new Shipment.Contact("Recipient", "0911111111", "Destination"),
                new Shipment.Parcel(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                new BigDecimal("25000"), PickupMethod.HOME_PICKUP, CreationChannel.ONLINE, PaymentMethod.CASH,
                null, new Shipment.Creation("customer:1", "key", "a".repeat(64), "customer", 1), null, now);
        var base = Snapshot.from(entity);
        local = new Snapshot(7L, base.trackingCode(), base.sender(), base.recipient(), base.parcel(), base.items(),
                base.shippingFee(), base.paymentMethod(), base.pickupMethod(), null, base.creationChannel(),
                null, now, SyncStatus.SYNCED, CancellationStatus.NONE, null, null);
        when(reader.detail(1, 7)).thenReturn(local);
    }

    /** Malformed enrichment never hides the already-read local snapshot or produces an AVAILABLE result. */
    @Test void Test_detail_001() {
        var invalid = Arrays.asList((ShipmentTracking) null,
                new ShipmentTracking("DC-OTHER", "SOURCE", now, List.of(), null),
                new ShipmentTracking("DC-OWNED", " ", now, List.of(), null),
                new ShipmentTracking("DC-OWNED", "SOURCE", null, List.of(), null),
                new ShipmentTracking("DC-OWNED", "SOURCE", now, null, null),
                new ShipmentTracking("DC-OWNED", "SOURCE", now, Collections.singletonList(null), null),
                new ShipmentTracking("DC-OWNED", "SOURCE", now,
                        List.of(new ShipmentTracking.PublicEvent(null, now, "Area")), null),
                new ShipmentTracking("DC-OWNED", "SOURCE", now,
                        List.of(new ShipmentTracking.PublicEvent("SOURCE", null, "Area")), null));
        for (var response : invalid) {
            when(shipping.getTracking("DC-OWNED")).thenReturn(response);
            assertThatCode(() -> {
                var detail = service.detail(customer, 7);
                assertThat(detail.shipment()).isSameAs(local);
                assertThat(detail.tracking().availability()).isEqualTo(Availability.UNAVAILABLE);
                assertThat(detail.tracking().errorCode()).isEqualTo("EXTERNAL_INVALID_RESPONSE");
            }).doesNotThrowAnyException();
        }
    }

    /** Java Instant values outside MySQL DATETIME are rejected before a reader call. */
    @Test void Test_list_001() {
        for (var date : List.of(Instant.parse("0999-12-31T23:59:59Z"), Instant.parse("+10000-01-01T00:00:00Z"),
                Instant.parse("9999-12-31T23:59:59.999999999Z"))) {
            for (boolean from : List.of(true, false)) {
                var query = new ShipmentListQuery(0, 20, null, null, null, from ? date : null,
                        from ? null : date, null, null, null);
                assertThatThrownBy(() -> service.list(customer, query))
                        .isInstanceOfSatisfying(ShipmentQueryException.class,
                                error -> assertThat(error.code()).isEqualTo(ShipmentQueryException.Code.INVALID_SHIPMENT_QUERY));
            }
        }
        verify(reader, never()).list(anyLong(), any());
    }

    /** The two supported MySQL DATETIME boundaries remain valid filter values. */
    @Test void Test_list_002() {
        var result = new ShipmentListResponse(List.of(), 0, 20, 0, 0, new ShipmentListResponse.Capabilities(true, false, false));
        when(reader.list(anyLong(), any())).thenReturn(result);
        var query = new ShipmentListQuery(0, 20, null, null, null, Instant.parse("1000-01-01T00:00:00Z"),
                Instant.parse("9999-12-31T23:59:59.999999Z"), null, null, null);
        assertThat(service.list(customer, query)).isSameAs(result);
    }
}
