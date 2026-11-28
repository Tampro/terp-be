package com.terp.erp.order;

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

    public static void as(Long companyId, String... permissions) {
        ErpPrincipal p = new ErpPrincipal(5L, "kc-5", "u5", companyId, false, Set.of(), Set.of(permissions));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
