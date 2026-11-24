package com.dcpost.customer.integration.shipping.dto;
public record SubmissionLookup(Status status) {
    public enum Status { FOUND, NOT_FOUND, UNKNOWN, UNSUPPORTED }
}
