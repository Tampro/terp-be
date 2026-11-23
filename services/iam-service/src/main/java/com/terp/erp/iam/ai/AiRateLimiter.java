package com.terp.erp.iam.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Compose calls per company per minute (fixed one-minute window). Protects the company's provider bill and quota
 * from a runaway client. TODO: in memory, so each IAM instance counts on its own; move to a shared store (Redis /
 * gateway rate limiter) when IAM runs more than one instance.
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {
    private record Window(long minute, int count) {
    }

    private final ConcurrentMap<Long, Window> windows = new ConcurrentHashMap<>();
    private final AiProperties props;
    private final Clock clock;

    /** True and counted when the company still has room this minute. */
    public boolean tryAcquire(Long companyId) {
        long minute = clock.millis() / 60_000L;
        int limit = props.requestsPerMinute();
        boolean[] ok = {false};
        windows.compute(companyId, (k, w) -> {
            Window cur = w == null || w.minute() != minute ? new Window(minute, 0) : w;
            if (cur.count() >= limit) {
                return cur;
            }
            ok[0] = true;
            return new Window(minute, cur.count() + 1);
        });
        return ok[0];
    }
}
