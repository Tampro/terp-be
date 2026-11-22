package com.terp.erp.attendance.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Weekly days off per company (PO, 2026-11-19); a company that never saved one has Saturday and Sunday. Also the IP
 * restriction of self clock-in/out (FR-ERP-ATT-004).
 */
@Service
@RequiredArgsConstructor
public class AttendanceSettingService {
    private final AttendanceSettingRepository settings;

    /** Working days of FR-ERP-ATT-004 = the days of the week not in weeklyOffDays. */
    public record SettingsDto(Set<DayOfWeek> weeklyOffDays, boolean ipRestrictionEnabled, List<String> allowedIps) {
        static SettingsDto of(AttendanceSetting s) {
            return new SettingsDto(s.offDays(), s.isIpRestrictionEnabled(), s.allowedIpList());
        }
    }

    public record SettingsForm(
            @Schema(description = "Weekly non-working days, 1 to 6", example = "[\"SATURDAY\", \"SUNDAY\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Set<DayOfWeek> weeklyOffDays,
            @Schema(description = "Self clock-in/out only from allowedIps; omitted = off")
            boolean ipRestrictionEnabled,
            @Schema(description = "IPv4/IPv6 addresses or CIDR ranges, at most 100; omitted = none",
                    example = "[\"203.0.113.7\", \"10.0.0.0/24\"]")
            @Size(max = IpRules.MAX_ENTRIES) List<String> allowedIps) {
        /** The form of earlier clients, which knew only the days off. */
        public SettingsForm(Set<DayOfWeek> weeklyOffDays) {
            this(weeklyOffDays, false, null);
        }
    }

    @Transactional(readOnly = true)
    public SettingsDto get(Long requestedCompanyId) {
        return of(company(requestedCompanyId));
    }

    /** Settings of a company already resolved by the caller (no tenant check here); defaults when never saved. */
    @Transactional(readOnly = true)
    public SettingsDto of(Long companyId) {
        return settings.findByCompanyId(companyId).map(SettingsDto::of)
                .orElse(new SettingsDto(EnumSet.copyOf(AttendanceSetting.DEFAULT_OFF_DAYS), false, List.of()));
    }

    /**
     * ATT_054 (403) when the company restricts self clock-in/out by IP and clientIp is not in its list (an unknown
     * client IP is refused too).
     */
    @Transactional(readOnly = true)
    public void assertClockIpAllowed(Long companyId, String clientIp) {
        SettingsDto s = of(companyId);
        if (s.ipRestrictionEnabled() && !IpRules.allowed(clientIp, s.allowedIps())) {
            throw ApiException.forbidden(EAttendanceError.ATT_054);
        }
    }

    @Transactional
    public SettingsDto save(Long requestedCompanyId, SettingsForm form) {
        if (form.weeklyOffDays().isEmpty() || form.weeklyOffDays().size() > 6) {
            throw ApiException.badRequest(EAttendanceError.ATT_017);
        }
        List<String> ips = IpRules.validate(form.allowedIps());
        if (form.ipRestrictionEnabled() && ips.isEmpty()) {
            throw ApiException.badRequest(EAttendanceError.ATT_059);
        }
        Long companyId = company(requestedCompanyId);
        AttendanceSetting s = settings.findByCompanyId(companyId).orElseGet(() -> {
            AttendanceSetting n = new AttendanceSetting();
            n.setCompanyId(companyId);
            return n;
        });
        s.setOffDays(form.weeklyOffDays());
        s.setIpRestrictionEnabled(form.ipRestrictionEnabled());
        s.setAllowedIps(ips.isEmpty() ? null : String.join(",", ips));
        settings.save(s);
        return SettingsDto.of(s);
    }

    /** Off days of a company already resolved by the caller (no tenant check here). */
    @Transactional(readOnly = true)
    public Set<DayOfWeek> offDays(Long companyId) {
        return settings.findByCompanyId(companyId).map(AttendanceSetting::offDays)
                .orElse(EnumSet.copyOf(AttendanceSetting.DEFAULT_OFF_DAYS));
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
