package com.dcpost.customer.shipment.dto;

import com.dcpost.customer.shipment.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateShipmentRequest(@NotNull @Valid Contact sender, @NotNull @Valid Contact recipient,
        @NotNull @Valid Parcel parcel, @NotEmpty List<@NotNull @Valid Item> items,
        @NotNull PickupMethod pickupMethod, @Positive Integer dropOffWarehouseId,
        @NotNull PaymentMethod paymentMethod, @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 0)
        BigDecimal confirmedFee, @Size(max = 16383) String note) {
    public CreateShipmentRequest {
        note = text(note); confirmedFee = decimal(confirmedFee);
        items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
    }
    public record Contact(@NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "0[0-9]{9,10}") String phone,
            @NotBlank @Size(max = 500) String address) {
        public Contact {
            name = text(name); address = text(address);
            if (phone != null) {
                phone = phone.replaceAll("[\\s()-]", "");
                if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
            }
        }
    }
    public record Parcel(@NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal weightKg,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal lengthCm,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal widthCm,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal heightCm) {
        public Parcel {
            weightKg = decimal(weightKg); lengthCm = decimal(lengthCm);
            widthCm = decimal(widthCm); heightCm = decimal(heightCm);
        }
    }
    public record Item(@NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 100) String category,
            @NotNull @Positive Integer quantity, @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2)
            BigDecimal declaredValue, @Size(max = 500) String note) {
        public Item {
            name = text(name); category = text(category); note = text(note); declaredValue = decimal(declaredValue);
        }
    }
    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
    private static BigDecimal decimal(BigDecimal value) { return value == null ? null : value.stripTrailingZeros(); }

    @Override public String toString() { return "CreateShipmentRequest[redacted]"; }
}
