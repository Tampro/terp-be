package com.terp.erp.attendance.regularization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class RegularizationDtos {
    private RegularizationDtos() {
    }

    public record EmployeeRef(String id, String fullName, String profileImageUrl) {
    }

    /** requestedOn = createdDate (the card's "Requested On"). */
    public record RegularizationDto(String id, EmployeeRef employee, String attendanceRecordId, LocalDate date,
                                    LocalTime originalClockIn, LocalTime originalClockOut,
                                    LocalTime requestedClockIn, LocalTime requestedClockOut, String reason,
                                    ERegularizationStatus status, String managerComments, Instant decidedAt,
                                    Instant createdDate, Instant updatedDate) {
        static RegularizationDto of(Regularization g, EmployeeRef employee) {
            return new RegularizationDto(g.getId().toString(), employee, g.getAttendanceRecordId().toString(),
                    g.getWorkDate(), g.getOriginalClockIn(), g.getOriginalClockOut(), g.getRequestedClockIn(),
                    g.getRequestedClockOut(), g.getReason(), g.getStatus(), g.getManagerComments(), g.getDecidedAt(),
                    g.getCreatedDate(), g.getUpdatedDate());
        }
    }

    public record StatusCounts(long all, long pending, long approved, long rejected) {
    }

    /** approvalRate = approved / decided (approved + rejected) in percent, one decimal; 0 when nothing is decided. */
    public record Summary(long total, long pending, long approved, long rejected, BigDecimal approvalRate) {
    }

    public record RegularizationForm(
            @Schema(description = "Employee id (employee-service)", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "The record to correct (GET /attendance/records?employeeId=)", example = "40",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long attendanceRecordId,
            @Schema(example = "08:00", type = "string", format = "time", description = "Omitted = unchanged")
            LocalTime requestedClockIn,
            @Schema(example = "17:00", type = "string", format = "time", description = "Omitted = unchanged")
            LocalTime requestedClockOut,
            @Schema(example = "Biometric device malfunction", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record DecisionForm(@Schema(example = "Checked with the team lead") @Size(max = 1000) String comments) {
    }
}
