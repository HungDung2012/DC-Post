package com.dcpost.customer.shipment.service;

public class ShipmentQueryException extends RuntimeException {
    public enum Code { SHIPMENT_NOT_FOUND, INVALID_SHIPMENT_QUERY, FILTER_UNAVAILABLE }
    private final Code code;
    public ShipmentQueryException(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
