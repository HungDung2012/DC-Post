package com.dcpost.customer.shipment.dto;

import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShipmentListResponse(List<Summary> content, int page, int size, long totalElements,
        int totalPages, Capabilities capabilities) {
    public ShipmentListResponse { content = List.copyOf(content); }
    public record Capabilities(boolean paymentMethodFilter, boolean paymentStatusFilter, boolean shippingStatusFilter) {}
    public record Summary(Long shipmentId, String trackingCode, String recipientName, String recipientPhone,
            BigDecimal shippingFee, PaymentMethod paymentMethod, PickupMethod pickupMethod,
            SyncStatus syncStatus, CancellationStatus cancellationStatus, Instant createdAt) {
        public static Summary from(Shipment shipment) {
            return new Summary(shipment.getId(), shipment.getTrackingCode(), shipment.getRecipientName(),
                    shipment.getRecipientPhone(), shipment.getShippingFee(), shipment.getPaymentMethod(),
                    shipment.getPickupMethod(), shipment.getSyncStatus(), shipment.getCancellationStatus(), shipment.getCreatedAt());
        }
    }
}
