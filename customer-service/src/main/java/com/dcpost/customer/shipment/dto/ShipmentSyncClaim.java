package com.dcpost.customer.shipment.dto;

import com.dcpost.customer.integration.shipping.dto.ShipmentSubmission;
import java.time.Instant;

public record ShipmentSyncClaim(long shipmentId, int attempt, Instant startedAt,
        String requestKey, ShipmentSubmission submission) {}
