package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDate;

/** A warning issued to an employee, with an optional improvement plan (docs/analysis/06 §7.2.4). */
@Getter
@Setter
@Entity
@Table(name = "employee_warning")
public class EmployeeWarning extends BaseAuditableEntity {
    public enum EStatus { DRAFT, ISSUED, ACKNOWLEDGED, EXPIRED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** The manager issuing the warning (an employee of the company). */
    @Column(name = "warned_by_id")
    private Long warnedById;

    @Column(name = "warning_type_id", nullable = false)
    private Long warningTypeId;

    @Column(name = "severity_id", nullable = false)
    private Long severityId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "warning_date", nullable = false)
    private LocalDate warningDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "document_media_id")
    private Long documentMediaId;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    @Column(name = "acknowledgment_date")
    private LocalDate acknowledgmentDate;

    @Column(name = "employee_response", columnDefinition = "TEXT")
    private String employeeResponse;

    @Column(name = "has_improvement_plan", nullable = false)
    private boolean hasImprovementPlan;

    @Column(name = "improvement_plan_goals", columnDefinition = "TEXT")
    private String improvementPlanGoals;

    @Column(name = "improvement_plan_start")
    private LocalDate improvementPlanStart;

    @Column(name = "improvement_plan_end")
    private LocalDate improvementPlanEnd;

    @Column(name = "improvement_plan_progress", columnDefinition = "TEXT")
    private String improvementPlanProgress;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
