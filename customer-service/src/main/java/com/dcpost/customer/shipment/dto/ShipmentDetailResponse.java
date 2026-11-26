package com.dcpost.customer.shipment.dto;

import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShipmentDetailResponse(Snapshot shipment, Tracking tracking) {
    public record Contact(String name, String phone, String address) {}
    public record Parcel(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
    public record Item(Long itemId, String name, String category, int quantity, BigDecimal declaredValue, String note) {}
    public record Snapshot(Long shipmentId, String trackingCode, Contact sender, Contact recipient, Parcel parcel,
            List<Item> items, BigDecimal shippingFee, PaymentMethod paymentMethod, PickupMethod pickupMethod,
            Integer dropOffWarehouseId, CreationChannel creationChannel, String note, Instant createdAt,
            SyncStatus syncStatus, CancellationStatus cancellationStatus, String cancelReason, Instant cancelledAt) {
        public Snapshot { items = List.copyOf(items); }
        public static Snapshot from(Shipment shipment) {
            return new Snapshot(shipment.getId(), shipment.getTrackingCode(),
                    new Contact(shipment.getSenderName(), shipment.getSenderPhone(), shipment.getSenderAddress()),
                    new Contact(shipment.getRecipientName(), shipment.getRecipientPhone(), shipment.getRecipientAddress()),
                    new Parcel(shipment.getWeight(), shipment.getLength(), shipment.getWidth(), shipment.getHeight()),
                    shipment.getItems().stream().map(item -> new Item(item.getId(), item.getName(), item.getCategory(),
                            item.getQuantity(), item.getDeclaredValue(), item.getNote())).toList(),
                    shipment.getShippingFee(), shipment.getPaymentMethod(), shipment.getPickupMethod(),
                    shipment.getDropOffWarehouseId(), shipment.getCreationChannel(), shipment.getNote(), shipment.getCreatedAt(),
                    shipment.getSyncStatus(), shipment.getCancellationStatus(), shipment.getCancelReason(), shipment.getCancelledAt());
        }
    }
    public enum Availability { NOT_READY, AVAILABLE, UNAVAILABLE }
    public record TrackingEvent(String sourceStatus, Instant occurredAt, String area) {}
    public record Tracking(Availability availability, String sourceStatus, Instant updatedAt,
            List<TrackingEvent> events, String errorCode) {
        public Tracking { events = List.copyOf(events); }
    }
}
