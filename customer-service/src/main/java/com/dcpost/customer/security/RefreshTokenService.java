package com.dcpost.customer.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
    private final SecureRandom random = new SecureRandom();

    public String issue(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public long userId(String token) {
        if (token == null || !token.matches("[1-9][0-9]{0,18}\\.[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException("Phiên không hợp lệ");
        }
        try {
            return Long.parseLong(token.substring(0, token.indexOf('.')));
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Phiên không hợp lệ");
        }
    }

    public boolean matches(String token, String expectedHash) {
        return token != null && expectedHash != null && MessageDigest.isEqual(
                hash(token).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
