package com.dcpost.customer.shipment.service;

import com.dcpost.customer.integration.shipping.ShippingClient;
import com.dcpost.customer.integration.shipping.dto.SubmissionResult;
import com.dcpost.customer.integration.shipping.dto.SubmissionLookup;
import com.dcpost.customer.integration.common.IntegrationException;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NEVER)
public class ShipmentSyncService {
    private final ShipmentSyncStateService state;
    private final ShippingClient shipping;
    private final Clock clock;

    public ShipmentSyncService(ShipmentSyncStateService state, ShippingClient shipping, Clock clock) {
        this.state = state; this.shipping = shipping; this.clock = clock;
    }

    public void sync(long shipmentId) {
        var claimed = state.tryClaim(shipmentId, clock.instant());
        if (claimed.isEmpty()) return;
        var claim = claimed.get();
        SubmissionResult result;
        try {
            result = shipping.submit(claim.submission(), claim.requestKey());
        } catch (IntegrationException exception) {
            state.fail(claim, exception.failureKind(), clock.instant());
            return;
        }
        state.finish(claim, result == null ? null : result.outcome(), clock.instant());
    }

    public void recover(long shipmentId) {
        var claimed = state.tryRecoveryClaim(shipmentId, clock.instant());
        if (claimed.isEmpty()) return;
        var claim = claimed.get();
        SubmissionLookup result;
        try {
            result = shipping.findSubmission(claim.trackingCode());
        } catch (IntegrationException exception) {
            state.failRecovery(claim, exception.failureKind(), clock.instant());
            return;
        }
        var status = result == null ? null : result.status();
        boolean safeRetry = status == SubmissionLookup.Status.NOT_FOUND && shipping.supportsSafeResubmission();
        state.finishRecovery(claim, status, safeRetry, clock.instant());
    }
}
