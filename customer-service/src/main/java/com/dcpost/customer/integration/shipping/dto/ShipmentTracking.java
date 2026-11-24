package com.dcpost.customer.integration.shipping.dto;
import java.time.Instant;
import java.util.List;
/** Public consumers must map only PublicEvent fields; private data is kept separate. */
public record ShipmentTracking(String trackingCode, String sourceStatus, Instant updatedAt,
        List<PublicEvent> publicEvents, PrivateDetails privateDetails) {
    public record PublicEvent(String sourceStatus, Instant occurredAt, String area) {}
    public record PrivateDetails(String recipientName, String recipientPhone, String recipientAddress) {}
}
