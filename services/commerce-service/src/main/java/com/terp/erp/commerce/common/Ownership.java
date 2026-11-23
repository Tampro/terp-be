package com.terp.erp.commerce.common;

import com.terp.erp.exception.ErrorCode;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.OwnScope;

/**
 * "Manage Own" on commerce documents: the owner of a document is its creator ({@code created_by} = Keycloak sub).
 * A result of {@code null} means "every document of the company"; a sub means "only documents this sub created".
 */
public final class Ownership {
    /**
     * Which permission pair a by-id access is checked against: VIEW (X_VIEW / X_VIEW_OWN), MANAGE (X_MANAGE /
     * X_MANAGE_OWN), POST (an action gated by X_POST, or a source document: see {@link #ownerLenient}).
     */
    public enum Access { VIEW, MANAGE, POST }

    private Ownership() {
    }

    /** Owner filter for module prefix {@code p} (QUOTATION, SALES, PURCHASE) and an access. */
    public static String owner(String p, Access access) {
        return switch (access) {
            case VIEW -> owner(p + "_VIEW", p + "_VIEW_OWN");
            case MANAGE -> owner(p + "_MANAGE", p + "_MANAGE_OWN");
            case POST -> ownerLenient(new String[]{p + "_VIEW", p + "_MANAGE"},
                    new String[]{p + "_VIEW_OWN", p + "_MANAGE_OWN"});
        };
    }

    /** Owner filter for a handler gated by {@code hasAny(full, own)}: ALL → null, OWN → my sub (else 403). */
    public static String owner(String fullCode, String ownCode) {
        return OwnScope.ownOnly(fullCode, ownCode) ? OwnScope.sub() : null;
    }

    /**
     * Owner filter for an action gated by another permission (SALES_POST, PURCHASE_POST, PAYMENT_CLEAR) or for a
     * source document reached through another one (the invoice of a return). Holding any full code of the pair list
     * (or super admin) → every document; holding only own codes → only mine; holding none of them → every document,
     * as before Manage Own existed (a pure poster keeps reaching every draft it is asked to post).
     * TODO(assumption): a POST holder with only *_OWN view/manage posts only their own documents — confirm with BA.
     */
    public static String ownerLenient(String[] fullCodes, String[] ownCodes) {
        ErpPrincipal p = CurrentUser.get();
        if (p.superAdmin()) {
            return null;
        }
        for (String c : fullCodes) {
            if (p.permissions().contains(c)) {
                return null;
            }
        }
        for (String c : ownCodes) {
            if (p.permissions().contains(c)) {
                return p.keycloakId();
            }
        }
        return null;
    }

    /** By-id access after {@code TenantGuard.assertSameCompany}: another user's document answers 404. */
    public static void check(String owner, String createdBy, ErrorCode notFound) {
        if (owner != null) {
            OwnScope.assertMine(owner.equals(createdBy), notFound);
        }
    }
}
