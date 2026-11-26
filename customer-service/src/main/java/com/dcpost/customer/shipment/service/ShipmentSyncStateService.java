package com.dcpost.customer.shipment.service;

import com.dcpost.customer.integration.shipping.dto.OperationOutcome;
import com.dcpost.customer.integration.common.IntegrationException;
import com.dcpost.customer.integration.shipping.dto.ShipmentSubmission;
import com.dcpost.customer.shipment.dto.ShipmentSyncClaim;
import com.dcpost.customer.shipment.dto.ShipmentRecoveryClaim;
import com.dcpost.customer.shipment.config.ShipmentSyncProperties;
import com.dcpost.customer.integration.config.IntegrationProperties;
import com.dcpost.customer.integration.shipping.dto.SubmissionLookup;
import com.dcpost.customer.shipment.model.*;
import java.util.Objects;
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
    private final ShipmentSyncProperties policy;

    public ShipmentSyncStateService(ShipmentRepository shipments, ShipmentSyncProperties policy, IntegrationProperties integration) {
        if (policy.processingTimeout().compareTo(integration.connectTimeout().plus(integration.readTimeout())) <= 0)
            throw new IllegalArgumentException("Sync processing timeout must exceed connect plus read timeout");
        this.shipments = shipments; this.policy = policy;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> candidates(Instant now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Shipment sync limit must be between 1 and 100");
        return shipments.findSyncCandidates(now.truncatedTo(ChronoUnit.MICROS), limit);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> recoveryCandidates(Instant now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Shipment recovery limit must be between 1 and 100");
        var timestamp = now.truncatedTo(ChronoUnit.MICROS);
        return shipments.findSyncRecoveryCandidates(timestamp, timestamp.minus(policy.processingTimeout()), limit);
    }

    public Optional<ShipmentSyncClaim> tryClaim(long shipmentId, Instant now) {
        var timestamp = now.truncatedTo(ChronoUnit.MICROS);
        if (shipments.blockExhaustedCashSync(shipmentId, timestamp, policy.maxAttempts()) == 1) return Optional.empty();
        if (shipments.claimCashSync(shipmentId, timestamp, policy.maxAttempts()) != 1) return Optional.empty();
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

    public void finish(ShipmentSyncClaim claim, OperationOutcome outcome, Instant now) {
        var status = outcome == OperationOutcome.CONFIRMED ? "SYNCED"
                : outcome == OperationOutcome.REJECTED ? "BLOCKED" : "UNKNOWN";
        var error = outcome == OperationOutcome.CONFIRMED ? null
                : outcome == null ? "EXTERNAL_INVALID_RESPONSE" : "SHIPPING_" + outcome.name();
        shipments.finishSync(claim.shipmentId(), claim.attempt(), claim.startedAt(), status, error,
                status.equals("UNKNOWN") ? now.plus(policy.reconciliationDelay()) : null);
    }

    public void fail(ShipmentSyncClaim claim, IntegrationException.Kind kind, Instant now) {
        var status = kind == IntegrationException.Kind.UNAUTHORIZED ? "BLOCKED" : "UNKNOWN";
        var error = "EXTERNAL_" + kind.name();
        Instant next = status.equals("UNKNOWN") ? now.plus(policy.reconciliationDelay()) : null;
        // HttpConnectTimeoutException means no HTTP request reached the peer; other transport errors remain uncertain.
        if (kind == IntegrationException.Kind.CONNECT_TIMEOUT) {
            var current = shipments.findLockedById(claim.shipmentId()).orElse(null);
            if (current == null || current.getSyncStatus() != SyncStatus.PROCESSING || current.getSyncAttemptCount() != claim.attempt()
                    || !Objects.equals(current.getSyncStartedAt(), claim.startedAt())) return;
            status = "BLOCKED"; next = null;
            if (current.getSyncAttemptCount() >= policy.maxAttempts()) error = "SYNC_ATTEMPTS_EXHAUSTED";
            else if (!retryEligible(current)) error = "SYNC_RETRY_NOT_ELIGIBLE";
            else { status = "PENDING"; next = now.plus(policy.retryDelay(claim.attempt())); }
        }
        shipments.finishSync(claim.shipmentId(), claim.attempt(), claim.startedAt(), status, error, next);
    }

    public Optional<ShipmentRecoveryClaim> tryRecoveryClaim(long shipmentId, Instant now) {
        var timestamp = now.truncatedTo(ChronoUnit.MICROS);
        shipments.expireProcessingSync(shipmentId, timestamp.minus(policy.processingTimeout()), timestamp);
        if (shipments.claimSyncRecovery(shipmentId, timestamp, timestamp.plus(policy.processingTimeout())) != 1)
            return Optional.empty();
        var shipment = shipments.findById(shipmentId).orElseThrow();
        return Optional.of(new ShipmentRecoveryClaim(shipmentId, shipment.getSyncAttemptCount(),
                shipment.getSyncStartedAt(), shipment.getTrackingCode()));
    }

    public void finishRecovery(ShipmentRecoveryClaim claim, SubmissionLookup.Status status, boolean safeRetry, Instant now) {
        var current = shipments.findLockedById(claim.shipmentId()).orElse(null);
        if (current == null || current.getSyncStatus() != SyncStatus.UNKNOWN || current.getSyncAttemptCount() != claim.attempt()
                || !Objects.equals(current.getSyncStartedAt(), claim.startedAt())) return;
        var target = "UNKNOWN";
        var error = status == null ? "EXTERNAL_INVALID_RESPONSE" : "SHIPPING_LOOKUP_UNKNOWN";
        Instant next = now.plus(policy.reconciliationDelay());
        if (status == SubmissionLookup.Status.FOUND) {
            target = "SYNCED"; error = null; next = null;
        } else if (status == SubmissionLookup.Status.UNSUPPORTED) {
            target = "BLOCKED"; error = "SHIPPING_LOOKUP_UNSUPPORTED"; next = null;
        } else if (status == SubmissionLookup.Status.NOT_FOUND) {
            next = null;
            target = "BLOCKED";
            if (!safeRetry) error = "SHIPPING_RETRY_UNSUPPORTED";
            else if (current.getSyncAttemptCount() >= policy.maxAttempts()) error = "SYNC_ATTEMPTS_EXHAUSTED";
            else if (!retryEligible(current)) error = "SYNC_RETRY_NOT_ELIGIBLE";
            else {
                target = "PENDING"; error = null; next = now.plus(policy.retryDelay(claim.attempt()));
            }
        }
        shipments.finishSyncRecovery(claim.shipmentId(), claim.attempt(), claim.startedAt(), target, error, next);
    }

    public void failRecovery(ShipmentRecoveryClaim claim, IntegrationException.Kind kind, Instant now) {
        boolean blocked = kind == IntegrationException.Kind.UNAUTHORIZED;
        shipments.finishSyncRecovery(claim.shipmentId(), claim.attempt(), claim.startedAt(), blocked ? "BLOCKED" : "UNKNOWN",
                "EXTERNAL_" + kind.name(), blocked ? null : now.plus(policy.reconciliationDelay()));
    }

    private static boolean retryEligible(com.dcpost.customer.shipment.entity.Shipment shipment) {
        return shipment.getPaymentMethod() == PaymentMethod.CASH && shipment.getCancellationStatus() == CancellationStatus.NONE
                && shipment.getCancelledAt() == null;
    }
}
