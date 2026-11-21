package com.terp.erp.attendance.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeRef;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LeaveAppDtos {
    private LeaveAppDtos() {
    }

    public record EmployeeRef(String id, String fullName, String email, String employeeCode, String designation,
                              String profileImageUrl) {
    }

    /** attachment links only for callers who may open the media library. appliedOn = createdDate. */
    public record ApplicationDto(String id, EmployeeRef employee, LeaveTypeRef leaveType, String leavePolicyId,
                                 LocalDate startDate, LocalDate endDate, int days, String reason,
                                 String attachmentFileId, String attachmentName, String attachmentUrl,
                                 ERegularizationStatus status, String managerComments, Instant decidedAt,
                                 Instant appliedOn, Instant updatedDate) {
    }

    public record ApplicationForm(
            @Schema(description = "Employee id (employee-service)", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long leaveTypeId,
            @Schema(example = "2026-12-21", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2026-12-23", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(example = "Family trip", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 1000) String reason,
            @Schema(description = "Media library file id of the company") Long attachmentFileId) {
    }

    public record DecisionForm(@Schema(example = "Enjoy your trip") @Size(max = 1000) String comments) {
    }

    /** One leave bar of the calendar. */
    public record CalendarLeave(String id, LeaveTypeRef leaveType, LocalDate startDate, LocalDate endDate, int days,
                                ERegularizationStatus status) {
    }

    public record CalendarRow(EmployeeRef employee, List<CalendarLeave> leaves) {
    }

    public record Calendar(LocalDate from, LocalDate to, List<CalendarRow> rows) {
    }

    // ------------------------------------------------------------------ balances

    /** total = allocated + carriedForward + adjustment; available = total - used. */
    public record BalanceLine(LeaveTypeRef leaveType, BigDecimal allocated, BigDecimal carriedForward,
                              BigDecimal adjustment, BigDecimal total, BigDecimal used, BigDecimal available) {
    }

    public record BalanceCard(EmployeeRef employee, List<BalanceLine> balances) {
    }

    public record BalancePage(int year, Instant lastSyncedAt, List<BalanceCard> cards, int page, int size,
                              long totalElements) {
    }

    public record SyncDto(String id, int year, String syncedBy, Instant syncedAt, Instant recordedAt, int employees,
                          int balances) {
    }
}
