package com.terp.erp.attendance.shift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.attendance.common.EStatus;

import java.time.Instant;
import java.time.LocalTime;

public final class ShiftDtos {
    private ShiftDtos() {
    }

    /** workingMinutes = shift span (over midnight for a night shift) minus the break. */
    public record ShiftDto(String id, String companyId, String name, String description, LocalTime startTime,
                           LocalTime endTime, int breakDurationMinutes, LocalTime breakStartTime,
                           LocalTime breakEndTime, int gracePeriodMinutes, boolean nightShift, long workingMinutes,
                           EStatus status, Instant createdDate, Instant updatedDate) {
        static ShiftDto of(Shift s) {
            return new ShiftDto(s.getId().toString(), s.getCompanyId().toString(), s.getName(), s.getDescription(),
                    s.getStartTime(), s.getEndTime(), s.getBreakDurationMinutes(), s.getBreakStartTime(),
                    s.getBreakEndTime(), s.getGracePeriodMinutes(), s.isNightShift(), s.workingMinutes(),
                    s.getStatus(), s.getCreatedDate(), s.getUpdatedDate());
        }
    }

    /** For dropdowns (employee profile): active shifts. */
    public record ShiftOption(String id, String name, LocalTime startTime, LocalTime endTime, boolean nightShift) {
    }

    public record StatusCounts(long all, long active, long inactive) {
    }

    /** Summary cards: Total, Active, Night, Day. */
    public record Summary(long total, long active, long night, long day) {
    }

    public record ShiftForm(
            @Schema(example = "Morning Shift", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Standard office hours") @Size(max = 500) String description,
            @Schema(example = "08:00", type = "string", format = "time", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalTime startTime,
            @Schema(example = "17:00", type = "string", format = "time", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalTime endTime,
            @Schema(example = "60", description = "Total break in minutes") @Min(0) @Max(1440) int breakDurationMinutes,
            @Schema(example = "12:00", type = "string", format = "time") LocalTime breakStartTime,
            @Schema(example = "13:00", type = "string", format = "time") LocalTime breakEndTime,
            @Schema(example = "10", description = "Minutes after the start before an arrival is late")
            @Min(0) @Max(720) int gracePeriodMinutes,
            @Schema(description = "Runs overnight: the end time may be on the next day") boolean nightShift,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }
}
