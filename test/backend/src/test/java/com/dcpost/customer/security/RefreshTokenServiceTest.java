package com.dcpost.customer.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenServiceTest {
    private final RefreshTokenService tokens = new RefreshTokenService();

    /** Token ngẫu nhiên khác nhau, chứa user ID và lưu bằng SHA-256 thay vì plaintext. */
    @Test
    void Test_issue_001() {
        String first = tokens.issue(42);
        assertNotEquals(first, tokens.issue(42));
        assertEquals(42, tokens.userId(first));
        assertEquals(64, tokens.hash(first).length());
        assertTrue(tokens.matches(first, tokens.hash(first)));
        assertFalse(tokens.matches(tokens.issue(42), tokens.hash(first)));
    }

    /** Token sai cấu trúc hoặc user ID không hợp lệ bị từ chối. */
    @Test
    void Test_userId_001() {
        assertThrows(BadCredentialsException.class, () -> tokens.userId("invalid"));
        assertThrows(BadCredentialsException.class, () -> tokens.userId("-1." + "a".repeat(43)));
    }
}
