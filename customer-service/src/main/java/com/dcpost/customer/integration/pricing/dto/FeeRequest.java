package com.dcpost.customer.integration.pricing.dto;

import java.math.BigDecimal;

public record FeeRequest(String originAddress, String destinationAddress, BigDecimal weightKg,
        BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
