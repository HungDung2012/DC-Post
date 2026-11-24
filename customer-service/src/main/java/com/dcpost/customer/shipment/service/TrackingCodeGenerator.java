package com.dcpost.customer.shipment.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class TrackingCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return "DC" + HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
