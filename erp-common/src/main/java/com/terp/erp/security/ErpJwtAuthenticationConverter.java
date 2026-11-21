package com.terp.erp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Builds the Authentication from a Keycloak access token; the resolved principal travels as the token details. */
@RequiredArgsConstructor
public class ErpJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthorityResolver resolver;

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        ErpPrincipal principal = resolver.resolve(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>();
        principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        if (principal.superAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + ErpPrincipal.SUPER_ADMIN_ROLE));
        }
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        token.setDetails(principal);
        return token;
    }

    /** Realm roles from {@code realm_access.roles}; only {@code SUPER_ADMIN} is read from Keycloak. */
    @SuppressWarnings("unchecked")
    public static boolean isSuperAdmin(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
            return ((Collection<Object>) roles).contains(ErpPrincipal.SUPER_ADMIN_ROLE);
        }
        return false;
    }
}
