package com.terp.erp.security;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;

/**
 * Record-level scope on top of a permission: {@code X_VIEW} reaches every row of the company, {@code X_VIEW_OWN}
 * only rows the caller owns (created, assigned, or linked to their customer/vendor). Company isolation stays with
 * {@link TenantGuard}; this only narrows inside the company.
 */
public final class OwnScope {

    public enum Mode { ALL, OWN }

    private OwnScope() {
    }

    /** ALL for a super admin or a holder of {@code fullCode}, OWN for a holder of {@code ownCode}, else 403. */
    public static Mode resolve(String fullCode, String ownCode) {
        ErpPrincipal p = CurrentUser.get();
        if (p.superAdmin() || p.permissions().contains(fullCode)) {
            return Mode.ALL;
        }
        if (p.permissions().contains(ownCode)) {
            return Mode.OWN;
        }
        throw ApiException.forbidden(ECommonError.COMMON_004);
    }

    public static boolean ownOnly(String fullCode, String ownCode) {
        return resolve(fullCode, ownCode) == Mode.OWN;
    }

    /** IAM user id of the caller; null for a super admin without a local account. */
    public static Long userId() {
        return CurrentUser.get().userId();
    }

    /** Keycloak {@code sub} of the caller (what {@code created_by} stores). */
    public static String sub() {
        return CurrentUser.get().keycloakId();
    }

    /** Another user's row answers 404, like a row of another company: the id is not confirmed to exist. */
    public static void assertMine(boolean mine, ErrorCode notFound) {
        if (!mine) {
            throw ApiException.notFound(notFound);
        }
    }
}
