package com.terp.erp.employee.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDate;

/** A performance goal of an employee (docs/analysis/06 §7.3.2). */
@Getter
@Setter
@Entity
@Table(name = "employee_goal")
public class EmployeeGoal extends BaseAuditableEntity {
    /**
     * OVERDUE is never stored: a NOT_STARTED / IN_PROGRESS goal whose end date is before today (Vietnam time) reads
     * as OVERDUE. TODO(assumption): derived rather than a stored status — confirm with BA.
     */
    public enum EStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, OVERDUE }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "goal_type_id", nullable = false)
    private Long goalTypeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** As typed ("Complete 5 projects"). */
    @Column(name = "target", length = 200)
    private String target;

    /** 0–100. */
    @Column(name = "progress", nullable = false)
    private int progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.NOT_STARTED;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
