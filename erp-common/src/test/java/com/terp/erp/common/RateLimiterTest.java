package com.terp.erp.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {
    /** A clock the test moves by hand. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-19T00:00:00Z");

        void advanceSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void burstThenRefillAtTheRate() {
        TestClock clock = new TestClock();
        RateLimiter l = new RateLimiter(60, 3, clock);
        assertThat(l.tryAcquire("a")).isZero();
        assertThat(l.tryAcquire("a")).isZero();
        assertThat(l.tryAcquire("a")).isZero();
        assertThat(l.tryAcquire("a")).isEqualTo(1);
        clock.advanceSeconds(1);
        assertThat(l.tryAcquire("a")).isZero();
        assertThat(l.tryAcquire("a")).isPositive();
    }

    @Test
    void keysAreIndependentAndTheBucketNeverExceedsTheBurst() {
        TestClock clock = new TestClock();
        RateLimiter l = new RateLimiter(60, 2, clock);
        l.tryAcquire("a");
        l.tryAcquire("a");
        assertThat(l.tryAcquire("a")).isPositive();
        assertThat(l.tryAcquire("b")).isZero();
        clock.advanceSeconds(3600);
        assertThat(l.tryAcquire("a")).isZero();
        assertThat(l.tryAcquire("a")).isZero();
        assertThat(l.tryAcquire("a")).isPositive();
    }
}
