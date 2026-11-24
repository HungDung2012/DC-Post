package com.dcpost.customer.integration.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FeeQuote(BigDecimal amount, String currency, Instant receivedAt) {}
