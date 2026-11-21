package com.terp.erp.iam.login;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.LoginHistory;
import com.terp.erp.iam.model.LoginHistory.EUserType;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.LoginHistoryRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Login History (docs/analysis/06 §10.1.1; PO 2026-11-21). A successful login is recorded in its own transaction and
 * never breaks the login when recording fails. The IP is X-Client-IP, which the gateway sets from the connection
 * (replacing any client-sent value; X-Forwarded-For is passed on as sent, so it is not trusted); browser / OS come from
 * the User-Agent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {
    private final LoginHistoryRepository history;
    private final UserAccountRepository users;
    private final Clock clock;

    public record LoginDto(String id, String userId, String companyId, String username, EUserType userType,
                           String ipAddress, String browser, String os, String userAgent, Instant loginAt) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, HttpServletRequest request) {
        try {
            Optional<UserAccount> account = users.findByUsernameAndDeletedFalse(username);
            LoginHistory h = new LoginHistory();
            h.setUsername(username);
            h.setUserId(account.map(a -> a.getId()).orElse(null));
            h.setCompanyId(account.map(a -> a.getCompanyId()).orElse(null));
            h.setUserType(account.isPresent() ? EUserType.COMPANY_USER : EUserType.SUPER_ADMIN);
            h.setIpAddress(clientIp(request.getHeader("X-Client-IP"), request.getRemoteAddr()));
            String ua = request.getHeader("User-Agent");
            h.setUserAgent(ua == null ? null : ua.length() > 500 ? ua.substring(0, 500) : ua);
            h.setBrowser(browser(ua));
            h.setOs(os(ua));
            h.setLoginAt(clock.instant());
            history.save(h);
        } catch (RuntimeException e) {
            log.warn("Login history not recorded: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * A company user sees their company; a super admin every login (companyId = one company, userType filter).
     */
    @Transactional(readOnly = true)
    public Page<LoginDto> search(Long requestedCompanyId, Long userId, EUserType userType, String keyword,
                                 Pageable pageable) {
        ErpPrincipal me = CurrentUser.get();
        Long companyId = me.superAdmin() ? requestedCompanyId : me.companyId();
        return history.search(companyId, userId, me.superAdmin() ? userType : EUserType.COMPANY_USER, kw(keyword),
                pageable).map(LoginHistoryService::dto);
    }

    @Transactional(readOnly = true)
    public LoginDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** X-Client-IP set by the gateway, else the connection's address (a call that did not come through it). */
    public static String clientIp(String gatewayClientIp, String remote) {
        if (gatewayClientIp != null && !gatewayClientIp.isBlank() && gatewayClientIp.trim().length() <= 64) {
            return gatewayClientIp.trim();
        }
        return remote;
    }

    public static String browser(String ua) {
        if (ua == null) {
            return null;
        }
        if (ua.contains("Edg/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return "Other";
    }

    public static String os(String ua) {
        if (ua == null) {
            return null;
        }
        String u = ua.toLowerCase(Locale.ROOT);
        if (u.contains("android")) {
            return "Android";
        }
        if (u.contains("iphone") || u.contains("ipad")) {
            return "iOS";
        }
        if (u.contains("windows")) {
            return "Windows";
        }
        if (u.contains("mac os x") || u.contains("macintosh")) {
            return "macOS";
        }
        if (u.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }

    private static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }

    private static LoginDto dto(LoginHistory h) {
        return new LoginDto(h.getId().toString(), h.getUserId() == null ? null : h.getUserId().toString(),
                h.getCompanyId() == null ? null : h.getCompanyId().toString(), h.getUsername(), h.getUserType(),
                h.getIpAddress(), h.getBrowser(), h.getOs(), h.getUserAgent(), h.getLoginAt());
    }

    /** A company user only reaches their company's rows; a super admin's own logins are super-admin only. */
    private LoginHistory require(Long id) {
        LoginHistory h = history.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_019));
        if (h.getCompanyId() == null) {
            if (!CurrentUser.get().superAdmin()) {
                throw ApiException.notFound(EIamError.IAM_019);
            }
        } else {
            TenantGuard.assertSameCompany(h.getCompanyId(), EIamError.IAM_019);
        }
        return h;
    }
}
