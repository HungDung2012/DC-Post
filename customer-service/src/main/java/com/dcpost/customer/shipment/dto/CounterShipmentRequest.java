package com.dcpost.customer.shipment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Null;

public record CounterShipmentRequest(@NotNull @Positive Integer createdBy,
        @Null(message = "Customer linking is unavailable until its verification contract is agreed") Long customerId,
        @NotNull @Valid CreateShipmentRequest shipment) {
    @Override public String toString() { return "CounterShipmentRequest[redacted]"; }
}
