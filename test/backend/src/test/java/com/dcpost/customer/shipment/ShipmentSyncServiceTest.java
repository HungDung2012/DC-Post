package com.dcpost.customer.shipment;

import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.shipping.dto.*;
import com.dcpost.customer.shipment.dto.ShipmentSyncClaim;
import com.dcpost.customer.shipment.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ShipmentSyncServiceTest {
    final Instant now = Instant.parse("2026-11-26T00:00:00Z");
    final ShipmentSyncStateService state = mock(ShipmentSyncStateService.class);
    final ShippingClient shipping = mock(ShippingClient.class);
    final ShipmentSyncService service = new ShipmentSyncService(state, shipping, Clock.fixed(now, ZoneOffset.UTC));

    /** A claimed shipment is submitted once with its stable key and the confirmed result is persisted. */
    @Test void Test_sync_001() {
        var submission = new ShipmentSubmission("DC-SYNC", new ShipmentSubmission.Contact("Sender", "0900000000", "Origin"),
                new ShipmentSubmission.Contact("Recipient", "0911111111", "Destination"),
                new ShipmentSubmission.Parcel(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                "HOME_PICKUP", null, "Note", List.of(new ShipmentSubmission.Item("Book", "Goods", 1, BigDecimal.TEN, null)));
        var claim = new ShipmentSyncClaim(41, 1, now, "shipment:DC-SYNC", submission);
        when(state.tryClaim(41, now)).thenReturn(Optional.of(claim));
        when(shipping.submit(submission, claim.requestKey())).thenReturn(new SubmissionResult(OperationOutcome.CONFIRMED));

        service.sync(41);

        var order = inOrder(state, shipping);
        order.verify(state).tryClaim(41, now);
        order.verify(shipping).submit(submission, claim.requestKey());
        order.verify(state).finish(claim, OperationOutcome.CONFIRMED, now);
        verifyNoMoreInteractions(shipping);
    }

    /** A denied claim never causes an external submission or a result write. */
    @Test void Test_sync_002() {
        when(state.tryClaim(41, now)).thenReturn(Optional.empty());
        service.sync(41);
        verifyNoInteractions(shipping);
        verify(state).tryClaim(41, now);
        verifyNoMoreInteractions(state);
    }
}
