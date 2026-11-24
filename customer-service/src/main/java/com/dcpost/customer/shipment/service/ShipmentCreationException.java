package com.dcpost.customer.shipment.service;

import java.math.BigDecimal;

public class ShipmentCreationException extends RuntimeException {
    public enum Code { INVALID_SHIPMENT, FEE_CHANGED, IDEMPOTENCY_CONFLICT, TRACKING_UNAVAILABLE }
    private final Code code;
    private final BigDecimal currentFee;

    public ShipmentCreationException(Code code) { this(code, null); }
    public ShipmentCreationException(Code code, BigDecimal currentFee) {
        super(code.name()); this.code = code; this.currentFee = currentFee;
    }
    public Code code() { return code; }
    public BigDecimal currentFee() { return currentFee; }
}
