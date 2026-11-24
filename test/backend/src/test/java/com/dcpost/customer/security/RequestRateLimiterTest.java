package com.dcpost.customer.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestRateLimiterTest {
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");

    /** Vượt số request trong cửa sổ trả từ chối kèm Retry-After. */
    @Test
    void Test_allow_001() {
        when(clock.instant()).thenReturn(now);
        var limiter = new RequestRateLimiter(clock, 2, 2, Duration.ofMinutes(1));
        assertTrue(limiter.allow("one").allowed());
        assertTrue(limiter.allow("one").allowed());
        assertFalse(limiter.allow("one").allowed());
        assertEquals(60, limiter.allow("one").retryAfterSeconds());
    }

    /** Hết TTL giải phóng slot; capacity đầy không bị vượt giới hạn bộ nhớ. */
    @Test
    void Test_allow_002() {
        when(clock.instant()).thenReturn(now);
        var limiter = new RequestRateLimiter(clock, 1, 2, Duration.ofMinutes(1));
        assertTrue(limiter.allow("one").allowed());
        assertFalse(limiter.allow("two").allowed());
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertTrue(limiter.allow("two").allowed());
    }
}
