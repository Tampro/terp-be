package com.terp.erp.security;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Used from SpEL: {@code @PreAuthorize("@PermissionChecker.has('IAM_ROLE_VIEW')")}. Codes are {@code MODULE_ACTION}.
 * A super admin passes every check here; company isolation is still enforced by {@link TenantGuard}.
 */
@Component("PermissionChecker")
public class PermissionChecker {

    public boolean has(String permission) {
        return CurrentUser.find().map(p -> p.has(permission)).orElse(false);
    }

    public boolean hasAny(String... permissions) {
        return CurrentUser.find().map(p -> Arrays.stream(permissions).anyMatch(p::has)).orElse(false);
    }

    public boolean isSuperAdmin() {
        return CurrentUser.find().map(p -> p.superAdmin()).orElse(false);
    }

    /** A company user (not a super admin) holding the permission — for endpoints that act on "my company". */
    public boolean hasInCompany(String permission) {
        return CurrentUser.find().map(p -> p.companyId() != null && p.permissions().contains(permission)).orElse(false);
    }
}
