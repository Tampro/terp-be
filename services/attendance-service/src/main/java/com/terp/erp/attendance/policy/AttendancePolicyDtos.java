package com.terp.erp.attendance.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.attendance.common.EStatus;

import java.math.BigDecimal;
import java.time.Instant;

public final class AttendancePolicyDtos {
    private AttendancePolicyDtos() {
    }

    public record PolicyDto(String id, String companyId, String name, String description, int lateArrivalGraceMinutes,
                            int earlyDepartureGraceMinutes, BigDecimal halfDayThresholdHours,
                            BigDecimal overtimeRatePerHour, EStatus status, Instant createdDate, Instant updatedDate) {
        static PolicyDto of(AttendancePolicy p) {
            return new PolicyDto(p.getId().toString(), p.getCompanyId().toString(), p.getName(), p.getDescription(),
                    p.getLateArrivalGraceMinutes(), p.getEarlyDepartureGraceMinutes(), p.getHalfDayThresholdHours(),
                    p.getOvertimeRatePerHour(), p.getStatus(), p.getCreatedDate(), p.getUpdatedDate());
        }
    }

    /** For dropdowns (employee profile): active policies. */
    public record PolicyOption(String id, String name) {
    }

    public record StatusCounts(long all, long active, long inactive) {
    }

    /** Summary cards; averages over every policy of the company, 0 when there is none. */
    public record Summary(long total, long active, BigDecimal avgLateArrivalGraceMinutes,
                          BigDecimal avgOvertimeRatePerHour) {
    }

    public record PolicyForm(
            @Schema(example = "Standard Attendance Policy", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Default grace periods and overtime") @Size(max = 500) String description,
            @Schema(example = "15", description = "Minutes; omitted = 15") @Min(0) @Max(720) Integer lateArrivalGraceMinutes,
            @Schema(example = "15", description = "Minutes; omitted = 15") @Min(0) @Max(720)
            Integer earlyDepartureGraceMinutes,
            @Schema(example = "4.00", description = "Fewer worked hours make a Half Day; omitted = 4.00")
            @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal halfDayThresholdHours,
            @Schema(example = "50000", description = "Paid per overtime hour, in the company's currency",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal overtimeRatePerHour,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }
}
