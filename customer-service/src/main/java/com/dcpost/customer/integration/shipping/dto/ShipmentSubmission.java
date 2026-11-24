package com.dcpost.customer.integration.shipping.dto;
import java.math.BigDecimal;
import java.util.List;
public record ShipmentSubmission(String trackingCode, Contact sender, Contact recipient,
        Parcel parcel, String pickupMethod, String dropOffLocationId, String note, List<Item> items) {
    public record Contact(String name, String phone, String address) {}
    public record Parcel(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
    public record Item(String name, String category, int quantity, BigDecimal declaredValue, String note) {}
}
