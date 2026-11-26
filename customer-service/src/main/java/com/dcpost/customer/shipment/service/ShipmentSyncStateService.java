package com.dcpost.customer.shipment.service;

import com.dcpost.customer.integration.shipping.dto.OperationOutcome;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.integration.shipping.dto.ShipmentSubmission;
import com.dcpost.customer.shipment.dto.ShipmentSyncClaim;
import com.dcpost.customer.shipment.repository.ShipmentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ShipmentSyncStateService {
    private final ShipmentRepository shipments;

    public ShipmentSyncStateService(ShipmentRepository shipments) { this.shipments = shipments; }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> candidates(Instant now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Shipment sync limit must be between 1 and 100");
        return shipments.findSyncCandidates(now.truncatedTo(ChronoUnit.MICROS), limit);
    }

    public Optional<ShipmentSyncClaim> tryClaim(long shipmentId, Instant now) {
        if (shipments.claimCashSync(shipmentId, now.truncatedTo(ChronoUnit.MICROS)) != 1) return Optional.empty();
        var shipment = shipments.findById(shipmentId).orElseThrow();
        var submission = new ShipmentSubmission(shipment.getTrackingCode(),
                new ShipmentSubmission.Contact(shipment.getSenderName(), shipment.getSenderPhone(), shipment.getSenderAddress()),
                new ShipmentSubmission.Contact(shipment.getRecipientName(), shipment.getRecipientPhone(), shipment.getRecipientAddress()),
                new ShipmentSubmission.Parcel(shipment.getWeight(), shipment.getLength(), shipment.getWidth(), shipment.getHeight()),
                shipment.getPickupMethod().name(), shipment.getDropOffWarehouseId() == null ? null : shipment.getDropOffWarehouseId().toString(),
                shipment.getNote(), shipment.getItems().stream().map(item -> new ShipmentSubmission.Item(item.getName(),
                        item.getCategory(), item.getQuantity(), item.getDeclaredValue(), item.getNote())).toList());
        return Optional.of(new ShipmentSyncClaim(shipmentId, shipment.getSyncAttemptCount(), shipment.getSyncStartedAt(),
                "shipment:" + shipment.getTrackingCode(), submission));
    }

    public void finish(ShipmentSyncClaim claim, OperationOutcome outcome) {
        var status = outcome == OperationOutcome.CONFIRMED ? "SYNCED"
                : outcome == OperationOutcome.REJECTED ? "BLOCKED" : "UNKNOWN";
        var error = outcome == OperationOutcome.CONFIRMED ? null
                : outcome == null ? "EXTERNAL_INVALID_RESPONSE" : "SHIPPING_" + outcome.name();
        shipments.finishSync(claim.shipmentId(), claim.attempt(), claim.startedAt(), status, error);
    }

    public void fail(ShipmentSyncClaim claim, IntegrationException.Kind kind) {
        shipments.finishSync(claim.shipmentId(), claim.attempt(), claim.startedAt(),
                kind == IntegrationException.Kind.UNAUTHORIZED ? "BLOCKED" : "UNKNOWN", "EXTERNAL_" + kind.name());
    }
}
