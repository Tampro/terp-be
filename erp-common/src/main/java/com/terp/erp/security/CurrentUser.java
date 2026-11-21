package com.terp.erp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;

import java.util.Optional;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Optional<ErpPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof ErpPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static ErpPrincipal get() {
        return find().orElseThrow(() -> ApiException.unauthorized(ECommonError.COMMON_003));
    }
}
