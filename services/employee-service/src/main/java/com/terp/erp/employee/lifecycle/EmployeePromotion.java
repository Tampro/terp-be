package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A promotion to another designation, possibly in another branch / department (FR-ERP-REC-001: Branch -> Department ->
 * Designation). new_branch_id / new_department_id null on rows from before 2026-11-22 = the employee's own then.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_promotion")
public class EmployeePromotion extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "previous_designation_id")
    private Long previousDesignationId;

    /** As typed (prefilled with the current designation's name). */
    @Column(name = "previous_designation", length = 150)
    private String previousDesignation;

    @Column(name = "previous_branch_id")
    private Long previousBranchId;

    @Column(name = "previous_department_id")
    private Long previousDepartmentId;

    @Column(name = "new_branch_id")
    private Long newBranchId;

    @Column(name = "new_department_id")
    private Long newDepartmentId;

    @Column(name = "new_designation_id", nullable = false)
    private Long newDesignationId;

    @Column(name = "promotion_date", nullable = false)
    private LocalDate promotionDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "document_media_id")
    private Long documentMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EDecision status = EDecision.PENDING;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
