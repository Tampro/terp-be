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
import com.terp.erp.iam.model.LoginHistory.EDeviceType;
import com.terp.erp.iam.model.LoginHistory.EUserType;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.LoginHistoryRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Login History (docs/analysis/06 §10.1.1; PO 2026-11-21). A successful login is recorded in its own transaction and
 * never breaks the login when recording fails. The IP is X-Client-IP, which the gateway sets from the connection
 * (replacing any client-sent value; X-Forwarded-For is passed on as sent, so it is not trusted); browser / OS come from
 * the User-Agent, as does the device type; language from Accept-Language, timezone from the optional X-Client-Timezone
 * header the FE may send. City, country and ISP come from the offline GeoLite2 files of {@link GeoIpLookup} (no IP is
 * sent anywhere); without them, or for a private address, they stay empty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryService {
    private final LoginHistoryRepository history;
    private final UserAccountRepository users;
    private final Clock clock;
    private final GeoIpLookup geoIp;

    public record LoginDto(String id, String userId, String companyId, String username, EUserType userType,
                           String ipAddress, String browser, String os, EDeviceType deviceType, String language,
                           String timezone, String roleAtLogin, String city, String country, String isp,
                           String userAgent, Instant loginAt) {
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
            geoIp.lookup(h.getIpAddress()).ifPresent(g -> {
                h.setCity(g.city());
                h.setCountry(g.country());
                h.setIsp(g.isp());
            });
            String ua = request.getHeader("User-Agent");
            h.setUserAgent(ua == null ? null : ua.length() > 500 ? ua.substring(0, 500) : ua);
            h.setBrowser(browser(ua));
            h.setOs(os(ua));
            h.setDeviceType(deviceType(ua));
            h.setLanguage(language(request.getHeader("Accept-Language")));
            h.setTimezone(timezone(request.getHeader("X-Client-Timezone")));
            h.setRoleAtLogin(account.map(LoginHistoryService::roles).orElse("SUPER_ADMIN"));
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
    public Page<LoginDto> search(Long requestedCompanyId, Long userId, EUserType userType, String ipAddress,
                                 String keyword, Pageable pageable) {
        ErpPrincipal me = CurrentUser.get();
        Long companyId = me.superAdmin() ? requestedCompanyId : me.companyId();
        return history.search(companyId, userId, me.superAdmin() ? userType : EUserType.COMPANY_USER,
                ipAddress == null || ipAddress.isBlank() ? null : ipAddress.trim(), kw(keyword),
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

    public static EDeviceType deviceType(String ua) {
        if (ua == null || ua.isBlank()) {
            return EDeviceType.UNKNOWN;
        }
        String u = ua.toLowerCase(Locale.ROOT);
        if (u.contains("bot") || u.contains("crawler") || u.contains("spider") || u.contains("curl/")
                || u.contains("wget/")) {
            return EDeviceType.BOT;
        }
        if (u.contains("ipad") || u.contains("tablet") || (u.contains("android") && !u.contains("mobile"))) {
            return EDeviceType.TABLET;
        }
        if (u.contains("mobi") || u.contains("iphone") || u.contains("android")) {
            return EDeviceType.MOBILE;
        }
        if (u.contains("windows") || u.contains("macintosh") || u.contains("mac os x") || u.contains("linux")
                || u.contains("cros")) {
            return EDeviceType.DESKTOP;
        }
        return EDeviceType.UNKNOWN;
    }

    /** First tag of Accept-Language ("vi-VN,vi;q=0.9" gives vi-VN); null when absent or malformed. */
    public static String language(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        String tag = acceptLanguage.split(",")[0].split(";")[0].trim();
        return tag.length() <= 35 && tag.matches("[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*") ? tag : null;
    }

    /** A valid IANA zone id, else null (a bad header is ignored, never fails the login). */
    public static String timezone(String header) {
        if (header == null || header.isBlank() || header.trim().length() > 64) {
            return null;
        }
        try {
            return ZoneId.of(header.trim()).getId();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String roles(UserAccount a) {
        String codes = a.getRoles().stream().map(Role::getCode).sorted().collect(Collectors.joining(","));
        return codes.isEmpty() ? null : codes.length() > 255 ? codes.substring(0, 255) : codes;
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
                h.getIpAddress(), h.getBrowser(), h.getOs(), h.getDeviceType(), h.getLanguage(), h.getTimezone(),
                h.getRoleAtLogin(), h.getCity(), h.getCountry(), h.getIsp(), h.getUserAgent(), h.getLoginAt());
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
