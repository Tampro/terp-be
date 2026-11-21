package com.terp.erp.attendance.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.attendance.common.EStatus;

import java.time.Instant;

public final class LeaveDtos {
    private LeaveDtos() {
    }

    public record LeaveTypeDto(String id, String name, String description, int maxDaysPerYear, boolean paid,
                               String color, EStatus status, Instant createdDate, Instant updatedDate) {
        static LeaveTypeDto of(LeaveType t) {
            return new LeaveTypeDto(t.getId().toString(), t.getName(), t.getDescription(), t.getMaxDaysPerYear(),
                    t.isPaid(), t.getColor(), t.getStatus(), t.getCreatedDate(), t.getUpdatedDate());
        }
    }

    /** Dropdowns and the calendar legend: active leave types. */
    public record LeaveTypeOption(String id, String name, String color, boolean paid) {
    }

    public record LeaveTypeForm(
            @Schema(example = "Annual Leave", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Paid yearly leave") @Size(max = 1000) String description,
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @Min(0) @Max(366) int maxDaysPerYear,
            @Schema(description = "Paid leave: salary is kept for these days", defaultValue = "true") Boolean paid,
            @Schema(example = "#3B82F6", description = "#RRGGBB; omitted = #3B82F6")
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record LeaveTypeRef(String id, String name, String color) {
    }

    public record LeavePolicyDto(String id, String name, String description, LeaveTypeRef leaveType,
                                 int carryForwardLimit, int minDaysPerApplication, int maxDaysPerApplication,
                                 boolean requiresApproval, EStatus status, Instant createdDate, Instant updatedDate) {
    }

    public record LeavePolicyForm(
            @Schema(example = "Standard annual leave", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Up to 5 days move to next year") @Size(max = 1000) String description,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long leaveTypeId,
            @Schema(example = "5", description = "Unused days that may move to next year") @Min(0) @Max(366)
            int carryForwardLimit,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @Min(1) @Max(366)
            int minDaysPerApplication,
            @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED) @Min(1) @Max(366)
            int maxDaysPerApplication,
            @Schema(description = "false = applications are approved at once", defaultValue = "true")
            Boolean requiresApproval,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record StatusCounts(long all, long active, long inactive) {
    }
}
