package com.terp.erp.iam.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits the public account endpoints (forgot password, sign up, resend verification) so they cannot be used to
 * flood inboxes or create companies in bulk. Fixed 15-minute windows, in memory: per instance, reset on restart.
 * TODO(assumption): limits chosen by the developer (20 per IP, 3 per email address); confirm with PO.
 */
@Component
public class AuthRateLimiter {
    static final int PER_IP = 20;
    static final int PER_EMAIL = 3;

    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15)).maximumSize(100_000).build();

    /** 429 AUTH_013 once the IP used up its window. */
    public void checkIp(String action, String ip) {
        if (hit(action + "|ip|" + (ip == null ? "-" : ip)) > PER_IP) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, EIamError.AUTH_013);
        }
    }

    /**
     * false once the address used up its window. Callers that must not reveal whether an address exists answer as
     * usual and simply send nothing.
     */
    public boolean allowEmail(String action, String email) {
        return hit(action + "|mail|" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT))) <= PER_EMAIL;
    }

    private int hit(String key) {
        return counts.get(key, k -> new AtomicInteger()).incrementAndGet();
    }
}
