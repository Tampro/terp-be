package com.terp.erp.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Puts a fake caller into the security context. Fixtures are obviously fake — no real people. */
public final class TestPrincipals {
    private TestPrincipals() {
    }

    public static ErpPrincipal companyUser(long userId, long companyId, String... permissions) {
        return new ErpPrincipal(userId, "kc-" + userId, "user" + userId, companyId, false, Set.of("TEST"),
                Set.of(permissions));
    }

    public static ErpPrincipal superAdmin() {
        return new ErpPrincipal(null, "kc-root", "root", null, true, Set.of(), Set.of());
    }

    public static void login(ErpPrincipal p) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
