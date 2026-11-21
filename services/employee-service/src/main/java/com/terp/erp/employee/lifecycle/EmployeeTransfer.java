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

/** A move to another branch / department / designation (docs/analysis/06 §7.2.3); to_* is the full new placement. */
@Getter
@Setter
@Entity
@Table(name = "employee_transfer")
public class EmployeeTransfer extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "from_branch_id")
    private Long fromBranchId;

    @Column(name = "from_department_id")
    private Long fromDepartmentId;

    @Column(name = "from_designation_id")
    private Long fromDesignationId;

    @Column(name = "to_branch_id")
    private Long toBranchId;

    @Column(name = "to_department_id")
    private Long toDepartmentId;

    @Column(name = "to_designation_id")
    private Long toDesignationId;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

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
