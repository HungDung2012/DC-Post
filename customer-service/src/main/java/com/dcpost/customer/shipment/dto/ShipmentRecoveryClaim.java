package com.dcpost.customer.shipment.dto;

import java.time.Instant;

public record ShipmentRecoveryClaim(long shipmentId, int attempt, Instant startedAt, String trackingCode) {}
