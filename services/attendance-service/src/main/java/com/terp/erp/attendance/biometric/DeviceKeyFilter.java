package com.terp.erp.attendance.biometric;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.common.ApiMessageDto;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.terp.erp.attendance.config.DeviceRateLimitProperties;
import com.terp.erp.common.RateLimiter;
import com.terp.erp.exception.ECommonError;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Authenticates a biometric device by its X-Device-Key (no Keycloak token: devices cannot log in). Unknown, deleted or
 * inactive devices get 401 ATT_024. The key itself is never logged. Too many requests of one device, or too many
 * wrong keys from one client IP, get 429 COMMON_006 with Retry-After.
 */
public class DeviceKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Device-Key";

    /** Who is calling: a device of a company. */
    public record DevicePrincipal(Long deviceId, Long companyId) {
    }

    private final BiometricDeviceRepository devices;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean trustForwardedFor;
    /** Per device key: a device sends a few batches a minute. */
    private final RateLimiter perDevice;
    /** Wrong keys per client IP: refused keys are counted, the IP waits once it has used them up. */
    private final RateLimiter failedPerIp;
    private final Cache<String, Long> blockedUntil = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(100_000).build();

    public DeviceKeyFilter(BiometricDeviceRepository devices, ObjectMapper mapper, DeviceRateLimitProperties limits,
                           Clock clock) {
        this.devices = devices;
        this.mapper = mapper;
        this.clock = clock;
        this.trustForwardedFor = limits.trustForwardedFor();
        this.perDevice = new RateLimiter(limits.perMinute(), limits.burst(), clock);
        this.failedPerIp = new RateLimiter(limits.failedPerMinute(), limits.failedPerMinute(), clock);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String ip = clientIp(request);
        Long until = blockedUntil.getIfPresent(ip);
        if (until != null && until > clock.millis()) {
            tooMany(response, (until - clock.millis() + 999) / 1000);
            return;
        }
        String key = request.getHeader(HEADER);
        BiometricDevice d = key == null || key.isBlank() ? null : devices.findByApiKeyHashAndDeletedFalse(
                DeviceKeys.hash(key.trim())).filter(x -> DeviceKeys.same(x.getApiKeyHash(), DeviceKeys.hash(key.trim())))
                .filter(x -> x.getStatus() == EStatus.ACTIVE).orElse(null);
        if (d == null) {
            long wait = failedPerIp.tryAcquire(ip);
            if (wait > 0) {
                blockedUntil.put(ip, clock.millis() + wait * 1000);
                tooMany(response, wait);
                return;
            }
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getOutputStream(), ApiMessageDto.builder().result(false)
                    .code(EAttendanceError.ATT_024.getCode()).message(EAttendanceError.ATT_024.getMessage()).build());
            return;
        }
        long wait = perDevice.tryAcquire("device:" + d.getId());
        if (wait > 0) {
            tooMany(response, wait);
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(new DevicePrincipal(d.getId(), d.getCompanyId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_BIOMETRIC_DEVICE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** The gateway appends the address it saw to X-Forwarded-For: the last entry is the one a client cannot forge. */
    String clientIp(HttpServletRequest request) {
        String forwarded = trustForwardedFor ? request.getHeader("X-Forwarded-For") : null;
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private void tooMany(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiMessageDto.builder().result(false)
                .code(ECommonError.COMMON_006.getCode()).message(ECommonError.COMMON_006.getMessage()).build());
    }
}
