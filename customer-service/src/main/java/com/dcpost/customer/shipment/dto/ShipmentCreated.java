package com.dcpost.customer.shipment.dto;

import com.dcpost.customer.shipment.entity.Shipment;
import com.dcpost.customer.shipment.model.*;
import java.math.BigDecimal;

public record ShipmentCreated(Long shipmentId, String trackingCode, BigDecimal shippingFee,
        PaymentMethod paymentMethod, SyncStatus syncStatus) {
    public static ShipmentCreated from(Shipment shipment) {
        return new ShipmentCreated(shipment.getId(), shipment.getTrackingCode(), shipment.getShippingFee(),
                shipment.getPaymentMethod(), shipment.getSyncStatus());
    }
}
