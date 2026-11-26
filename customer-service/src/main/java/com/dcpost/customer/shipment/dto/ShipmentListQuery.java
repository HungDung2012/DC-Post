package com.dcpost.customer.shipment.dto;

import com.dcpost.customer.shipment.model.*;
import java.time.Instant;

public record ShipmentListQuery(int page, int size, String q, PaymentMethod paymentMethod, SyncStatus syncStatus,
        Instant createdFrom, Instant createdBefore, String shippingStatus, String paymentStatus, String sort) {
    public ShipmentListQuery { q = q == null ? null : q.strip(); }
}
