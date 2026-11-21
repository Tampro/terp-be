package com.terp.erp.security;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;

import java.util.Objects;

/**
 * Company isolation. An id in a path is a request, not an authorization: every read or write by id checks that the
 * row belongs to the caller's company. A mismatch answers 404 with the module's "does not exist" error.
 */
public final class TenantGuard {
    private TenantGuard() {
    }

    /**
     * The company the caller acts on. A company user always acts on their own company and cannot pick another one;
     * a super admin must name it.
     */
    public static Long resolveCompany(Long requestedCompanyId, ErrorCode missingCompany) {
        ErpPrincipal p = CurrentUser.get();
        if (p.superAdmin()) {
            if (requestedCompanyId == null) {
                throw ApiException.badRequest(missingCompany);
            }
            return requestedCompanyId;
        }
        if (p.companyId() == null) {
            throw ApiException.forbidden(missingCompany);
        }
        return p.companyId();
    }

    public static void assertSameCompany(Long rowCompanyId, ErrorCode notFound) {
        ErpPrincipal p = CurrentUser.get();
        if (p.superAdmin()) {
            return;
        }
        if (p.companyId() == null || !Objects.equals(p.companyId(), rowCompanyId)) {
            throw ApiException.notFound(notFound);
        }
    }
}
