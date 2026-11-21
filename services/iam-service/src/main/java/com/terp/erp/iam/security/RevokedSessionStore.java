package com.terp.erp.iam.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keycloak session ids ({@code sid}) that logged out. Access tokens are self-contained JWTs that stay valid until
 * {@code exp}; this list makes IAM reject them at once after logout. An entry lives as long as an access token can
 * (realm accessTokenLifespan, 15 min) — after that every token of the session has expired anyway.
 *
 * <p>In memory: correct for one IAM instance only. With several instances this must move to a shared store
 * (Redis), otherwise a logout on one instance is unknown to the others.
 */
@Component
public class RevokedSessionStore {
    private final Cache<String, Boolean> revoked;

    public RevokedSessionStore(@Value("${erp.security.access-token-lifespan:PT15M}") Duration accessTokenLifespan) {
        this.revoked = Caffeine.newBuilder().expireAfterWrite(accessTokenLifespan).maximumSize(100_000).build();
    }

    public void revoke(String sid) {
        if (sid != null && !sid.isBlank()) {
            revoked.put(sid, Boolean.TRUE);
        }
    }

    public boolean isRevoked(String sid) {
        return sid != null && revoked.getIfPresent(sid) != null;
    }
}
