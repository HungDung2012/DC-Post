package com.dcpost.customer.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RequestRateLimiter {
    public record Decision(boolean allowed, long retryAfterSeconds) {}
    private final Clock clock;
    private final int maximumKeys;
    private final int limit;
    private final Duration window;
    private final Map<String, Window> windows = new HashMap<>();

    private static class Window {
        private final Instant expiresAt;
        private int count;
        Window(Instant expiresAt) { this.expiresAt = expiresAt; }
    }

    public RequestRateLimiter(Clock clock, int maximumKeys, int limit, Duration window) {
        if (maximumKeys < 1 || limit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Rate limit settings must be positive");
        }
        this.clock = clock;
        this.maximumKeys = maximumKeys;
        this.limit = limit;
        this.window = window;
    }

    public synchronized Decision allow(String key) {
        Instant now = clock.instant();
        windows.values().removeIf(value -> !now.isBefore(value.expiresAt));
        Window bucket = windows.get(key);
        if (bucket == null) {
            if (windows.size() >= maximumKeys) return new Decision(false, Math.max(1, window.toSeconds()));
            bucket = new Window(now.plus(window));
            windows.put(key, bucket);
        }
        if (bucket.count >= limit) {
            long remainingMillis = Duration.between(now, bucket.expiresAt).toMillis();
            return new Decision(false, Math.max(1, (remainingMillis + 999) / 1000));
        }
        bucket.count++;
        return new Decision(true, 0);
    }
}
