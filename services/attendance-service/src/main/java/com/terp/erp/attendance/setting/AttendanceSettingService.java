package com.terp.erp.attendance.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** Weekly days off per company (PO, 2026-11-19); a company that never saved one has Saturday and Sunday. */
@Service
@RequiredArgsConstructor
public class AttendanceSettingService {
    private final AttendanceSettingRepository settings;

    public record SettingsDto(Set<DayOfWeek> weeklyOffDays) {
    }

    public record SettingsForm(
            @Schema(description = "Weekly non-working days, 1 to 6", example = "[\"SATURDAY\", \"SUNDAY\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Set<DayOfWeek> weeklyOffDays) {
    }

    @Transactional(readOnly = true)
    public SettingsDto get(Long requestedCompanyId) {
        return new SettingsDto(offDays(company(requestedCompanyId)));
    }

    @Transactional
    public SettingsDto save(Long requestedCompanyId, SettingsForm form) {
        if (form.weeklyOffDays().isEmpty() || form.weeklyOffDays().size() > 6) {
            throw ApiException.badRequest(EAttendanceError.ATT_017);
        }
        Long companyId = company(requestedCompanyId);
        AttendanceSetting s = settings.findByCompanyId(companyId).orElseGet(() -> {
            AttendanceSetting n = new AttendanceSetting();
            n.setCompanyId(companyId);
            return n;
        });
        s.setOffDays(form.weeklyOffDays());
        settings.save(s);
        return new SettingsDto(s.offDays());
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
