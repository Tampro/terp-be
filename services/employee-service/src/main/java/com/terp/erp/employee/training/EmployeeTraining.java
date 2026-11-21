package com.terp.erp.employee.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A training program assigned to an employee (docs/analysis/06 §7.4.1). */
@Getter
@Setter
@Entity
@Table(name = "employee_training")
public class EmployeeTraining extends BaseAuditableEntity {
    public enum EStatus { ASSIGNED, IN_PROGRESS, COMPLETED, FAILED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.ASSIGNED;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    /** The assigning user's employee profile; null when they have none (e.g. a super admin). */
    @Column(name = "assigned_by_id")
    private Long assignedById;

    @Column(name = "certificate_media_id")
    private Long certificateMediaId;

    /** 0–100 (%). */
    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    /** null = no result yet. */
    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
