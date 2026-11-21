package com.terp.erp.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a validated Keycloak token into an {@link ErpPrincipal}. The IAM service resolves from its own database;
 * every other service uses {@link RemoteAuthorityResolver}, which asks IAM with the caller's own token.
 */
public interface AuthorityResolver {
    ErpPrincipal resolve(Jwt jwt);
}
