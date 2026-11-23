package com.terp.erp.project;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TestLogin {
    private TestLogin() {
    }

    public static void as(Long companyId, String... permissions) {
        set(new ErpPrincipal(5L, "kc-5", "u5", companyId, false, Set.of(), Set.of(permissions)));
    }

    /** A company user with an explicit IAM user id and Keycloak sub (own-scope tests). */
    public static void user(Long userId, String sub, Long companyId, String... permissions) {
        set(new ErpPrincipal(userId, sub, "u" + userId, companyId, false, Set.of(), Set.of(permissions)));
    }

    public static void superAdmin() {
        set(new ErpPrincipal(null, "kc-root", "root", null, true, Set.of(), Set.of()));
    }

    private static void set(ErpPrincipal p) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
