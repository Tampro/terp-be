package com.terp.erp.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;
import java.time.Duration;

/**
 * In-memory token buckets per key (an IP, a device): {@code burst} requests at once, refilled at {@code perMinute}.
 * Copy of erp-common RateLimiter (the gateway is WebFlux and does not depend on erp-common). Per instance only:
 * a shared store (Redis) is needed when the gateway scales out.
 * Idle keys are forgotten after 10 minutes, so the map cannot grow without bound.
 */
final class RateLimiter {
    private final double ratePerMilli;
    private final double burst;
    private final Clock clock;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100_000).build();

    RateLimiter(int perMinute, int burst, Clock clock) {
        if (perMinute <= 0 || burst <= 0) {
            throw new IllegalArgumentException("perMinute and burst must be positive");
        }
        this.ratePerMilli = perMinute / 60_000.0;
        this.burst = burst;
        this.clock = clock;
    }

    /** @return 0 when allowed, otherwise the seconds to wait before the next request can pass */
    long tryAcquire(String key) {
        Bucket b = buckets.get(key, k -> new Bucket(burst, clock.millis()));
        synchronized (b) {
            long now = clock.millis();
            b.tokens = Math.min(burst, b.tokens + (now - b.at) * ratePerMilli);
            b.at = now;
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - b.tokens) / ratePerMilli / 1000));
        }
    }

    private static final class Bucket {
        private double tokens;
        private long at;

        private Bucket(double tokens, long at) {
            this.tokens = tokens;
            this.at = at;
        }
    }
}
