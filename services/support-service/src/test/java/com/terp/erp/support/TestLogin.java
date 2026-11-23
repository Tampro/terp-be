package com.terp.erp.support;

import com.terp.erp.security.ErpPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TestLogin {
    private TestLogin() {
    }

    /** User 5 of the company. */
    public static void as(Long companyId, String... permissions) {
        user(5L, companyId, permissions);
    }

    public static void user(Long userId, Long companyId, String... permissions) {
        set(new ErpPrincipal(userId, "kc-" + userId, "u" + userId, companyId, false, Set.of(), Set.of(permissions)));
    }

    /** A company user with an explicit IAM user id and Keycloak sub (own-scope tests). */
    public static void user(Long userId, String sub, Long companyId, String... permissions) {
        set(new ErpPrincipal(userId, sub, "u" + userId, companyId, false, Set.of(), Set.of(permissions)));
    }

    public static void superAdmin() {
        set(new ErpPrincipal(null, "kc-root", "root", null, true, Set.of(), Set.of()));
    }

    /** Like {@link #user(Long, String, Long, String...)} with an {@code email} claim in the token. */
    public static void userWithEmail(Long userId, String sub, Long companyId, String email, String... permissions) {
        set(new ErpPrincipal(userId, sub, "u" + userId, companyId, false, Set.of(), Set.of(permissions)),
                Map.of("sub", sub, "email", email, "name", "Test Client " + userId));
    }

    private static void set(ErpPrincipal p) {
        set(p, Map.of("sub", p.keycloakId()));
    }

    private static void set(ErpPrincipal p, Map<String, Object> claims) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
