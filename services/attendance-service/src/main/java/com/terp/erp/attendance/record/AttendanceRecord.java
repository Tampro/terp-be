package com.terp.erp.attendance.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One employee's attendance on one day (docs/analysis/06 §6.4.1, columns from doc 03 `attendance_records`). The flags
 * and hours are computed when the record is saved (AttendanceCalculator), from the shift and policy of the employee at
 * that moment, which are kept on the record. A clock-out before the clock-in is on the next day (night shift).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_record")
public class AttendanceRecord extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** employee-service id. */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "attendance_policy_id")
    private Long attendancePolicyId;

    @Column(name = "clock_in")
    private LocalTime clockIn;

    @Column(name = "clock_out")
    private LocalTime clockOut;

    /** The clock-out was set by AutoClockOutService at the shift end, not by the employee. */
    @Column(name = "auto_clocked_out", nullable = false)
    private boolean autoClockedOut;

    @Column(name = "total_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalHours = BigDecimal.ZERO;

    @Column(name = "break_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal breakHours = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "overtime_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    @Column(name = "is_late", nullable = false)
    private boolean late;

    @Column(name = "is_early_departure", nullable = false)
    private boolean earlyDeparture;

    @Column(name = "is_absent", nullable = false)
    private boolean absent;

    @Column(name = "is_holiday", nullable = false)
    private boolean holiday;

    @Column(name = "is_weekend", nullable = false)
    private boolean weekend;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ERecordStatus status = ERecordStatus.PRESENT;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Set when the record comes from biometric punches (6.4.3). */
    @Column(name = "biometric_id", length = 100)
    private String biometricId;
}
