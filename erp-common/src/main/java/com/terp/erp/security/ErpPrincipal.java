package com.terp.erp.security;

import java.util.Set;

/**
 * Who is calling, as the IAM service knows them. Keycloak proves identity ({@code sub}); company, roles and
 * permissions come from the IAM database, so a role change applies within the resolver cache TTL instead of
 * waiting for the token to expire.
 *
 * @param userId      IAM user id, null for a super admin without a local account
 * @param keycloakId  token {@code sub}
 * @param companyId   tenant of the caller, null for a super admin
 * @param superAdmin  realm role {@code SUPER_ADMIN}: operates the SaaS, outside any company
 */
public record ErpPrincipal(
        Long userId,
        String keycloakId,
        String username,
        Long companyId,
        boolean superAdmin,
        Set<String> roles,
        Set<String> permissions) {

    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    public boolean has(String permission) {
        return superAdmin || permissions.contains(permission);
    }
}
