package com.terp.erp.attendance.record;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class RecordDtos {
    private RecordDtos() {
    }

    public record RecordDto(String id, String employeeId, LocalDate date, LocalTime clockIn, LocalTime clockOut,
                            ERecordStatus status, boolean late, boolean earlyDeparture, boolean holiday,
                            boolean weekend, BigDecimal totalHours, BigDecimal breakHours, BigDecimal overtimeHours,
                            BigDecimal overtimeAmount, String shiftId, String attendancePolicyId, String notes,
                            Instant createdDate, Instant updatedDate) {
        static RecordDto of(AttendanceRecord r) {
            return new RecordDto(r.getId().toString(), r.getEmployeeId().toString(), r.getWorkDate(), r.getClockIn(),
                    r.getClockOut(), r.getStatus(), r.isLate(), r.isEarlyDeparture(), r.isHoliday(), r.isWeekend(),
                    r.getTotalHours(), r.getBreakHours(), r.getOvertimeHours(), r.getOvertimeAmount(),
                    str(r.getShiftId()), str(r.getAttendancePolicyId()), r.getNotes(), r.getCreatedDate(),
                    r.getUpdatedDate());
        }

        private static String str(Long v) {
            return v == null ? null : v.toString();
        }
    }

    public record RecordForm(
            @Schema(description = "Employee id (employee-service)", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long employeeId,
            @Schema(example = "2026-11-18", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate date,
            @Schema(example = "08:05", type = "string", format = "time", description = "Omitted = Absent")
            LocalTime clockIn,
            @Schema(example = "17:10", type = "string", format = "time",
                    description = "Before the clock-in = next day (night shift); omitted = still at work")
            LocalTime clockOut,
            @Schema(description = "Mark the day as a holiday") boolean holiday,
            @Schema(example = "Working from home") @Size(max = 500) String notes) {
    }

    /** One cell of the calendar; recordId null when no record exists. */
    public record DayCell(LocalDate date, EDayStatus status, boolean weekend, boolean late, boolean earlyDeparture,
                          boolean overtime, LocalTime clockIn, LocalTime clockOut, String notes, String recordId) {
    }

    /** attended = Present days + half of the Half Days; workingDays = days of the month neither off nor holiday. */
    public record EmployeeRow(String employeeId, String fullName, String employeeCode, String designation,
                              String profileImageUrl, List<DayCell> days, BigDecimal attended, int workingDays) {
    }

    public record Calendar(int year, int month, LocalDate today, java.util.Set<DayOfWeek> weeklyOffDays,
                           List<EmployeeRow> rows, int page, int size, long totalElements) {
    }

    public record ImportResult(int total, int created, int updated, List<RowError> errors) {
    }

    public record RowError(int row, String code, String message) {
    }
}
