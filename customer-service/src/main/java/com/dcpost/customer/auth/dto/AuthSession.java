package com.dcpost.customer.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record AuthSession(String accessToken, Instant expiresAt, CustomerAccount account,
                          @JsonIgnore String refreshToken, @JsonIgnore Instant refreshExpiresAt) {
    @Override
    public String toString() { return "AuthSession[redacted]"; }
}
