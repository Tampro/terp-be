package com.terp.erp.attendance.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Hours an employee logged against a project on a day (docs/analysis/06 §6.4.2, doc 03 `time_entries`). */
@Getter
@Setter
@Entity
@Table(name = "time_entry")
public class TimeEntry extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;

    /** Shown as the entry's Title in the day popup. */
    @Column(name = "project", length = 150)
    private String project;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    /** Same three states as a regularization request (doc 03). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ERegularizationStatus status = ERegularizationStatus.PENDING;

    @Column(name = "manager_comments", length = 1000)
    private String managerComments;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
