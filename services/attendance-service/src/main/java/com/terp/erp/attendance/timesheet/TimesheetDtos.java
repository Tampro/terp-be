package com.terp.erp.attendance.timesheet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TimesheetDtos {
    private TimesheetDtos() {
    }

    public record EntryDto(String id, String employeeId, LocalDate date, BigDecimal hours, ETimeEntryType type,
                           String project,
                           String description, ERegularizationStatus status, String managerComments,
                           Instant decidedAt, Instant createdDate, Instant updatedDate) {
        static EntryDto of(TimeEntry t) {
            return new EntryDto(t.getId().toString(), t.getEmployeeId().toString(), t.getWorkDate(), t.getHours(),
                    t.getEntryType(), t.getProject(), t.getDescription(), t.getStatus(), t.getManagerComments(), t.getDecidedAt(),
                    t.getCreatedDate(), t.getUpdatedDate());
        }
    }

    /** status of the day: PENDING if any entry waits, else REJECTED if any was rejected, else APPROVED; null = none. */
    public record DayCell(LocalDate date, BigDecimal hours, int entries, ERegularizationStatus status) {
    }

    public record WeekRow(String employeeId, String fullName, String designation, String profileImageUrl,
                          List<DayCell> days, BigDecimal total) {
    }

    public record StatusCounts(long all, long pending, long approved, long rejected) {
    }

    /** weekStart is the Monday of the requested week. */
    public record Week(LocalDate weekStart, LocalDate weekEnd, List<WeekRow> rows, StatusCounts counts, int page,
                       int size, long totalElements) {
    }

    public record EntryForm(
            @Schema(description = "Employee id (employee-service)", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "2026-11-18", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate date,
            @Schema(example = "6", description = "Hours (decimals allowed); with minutes the total is 15 minutes to 24 "
                    + "hours (ATT_053, ATT_031)", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 2) BigDecimal hours,
            @Schema(example = "ERP portal") @Size(max = 150) String project,
            @Schema(example = "Code review of the attendance module", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000) String description,
            @Schema(description = "CLOCK_IN_OUT, PROJECT or MANUAL (default)", example = "PROJECT") ETimeEntryType type,
            @Schema(description = "Minutes added to hours, 0 to 59", example = "30") @Min(0) @Max(59) Integer minutes) {
    }

    /** One employee's month: a cell per day of the month and the month's total. */
    public record MonthRow(String employeeId, String fullName, String designation, String profileImageUrl,
                           List<DayCell> days, BigDecimal total) {
    }

    /** dayTotals = hours of all rows per day (the footer); total = the whole grid. */
    public record Month(int year, int month, LocalDate from, LocalDate to, List<MonthRow> rows,
                        List<BigDecimal> dayTotals, BigDecimal total, StatusCounts counts, int page, int size,
                        long totalElements) {
    }

    public record DecisionForm(@Schema(example = "Confirmed with the project lead") @Size(max = 1000) String comments) {
    }

    public record ImportResult(int total, int created, List<RowError> errors) {
    }

    public record RowError(int row, String code, String message) {
    }
}
