package com.terp.erp.payroll.overtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manual overtime of an employee (manual 15 Set salary → Overtimes), apart from the overtime attendance records.
 * Only APPROVED overtime is paid, once: paidRunId is the run that paid it (cleared when that run is cancelled).
 */
@Getter
@Setter
@Entity
@Table(name = "employee_overtime")
public class EmployeeOvertime extends BaseAuditableEntity {
    public enum EStatus { PENDING, APPROVED, REJECTED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "days", nullable = false, precision = 7, scale = 2)
    private BigDecimal days;

    /** Hours per day. */
    @Column(name = "hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal hours;

    /** Amount per hour, company currency. */
    @Column(name = "rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.PENDING;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "paid_run_id")
    private Long paidRunId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
