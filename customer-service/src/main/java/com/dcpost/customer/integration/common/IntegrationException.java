package com.dcpost.customer.integration.common;

public final class IntegrationException extends RuntimeException {
    public enum Kind { CONNECT_TIMEOUT, READ_TIMEOUT, UNAVAILABLE, INVALID_RESPONSE, UNAUTHORIZED, UNKNOWN_OUTCOME }

    private final Kind kind;
    private final Kind failureKind;

    public IntegrationException(Kind failureKind, boolean mutation) {
        super("External integration failed: " + failureKind);
        this.failureKind = failureKind;
        this.kind = mutation && failureKind != Kind.UNAUTHORIZED ? Kind.UNKNOWN_OUTCOME : failureKind;
    }

    public Kind kind() { return kind; }
    public Kind failureKind() { return failureKind; }
}
