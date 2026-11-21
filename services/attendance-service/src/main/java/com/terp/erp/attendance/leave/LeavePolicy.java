package com.terp.erp.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.common.EStatus;

/**
 * The rules of one leave type (docs/analysis/06 §6.5.4): carry-forward, days per application, approval.
 * TODO(assumption): one ACTIVE policy per leave type (ATT_036), since the spec assigns policies to no employee —
 * confirm with BA.
 */
@Getter
@Setter
@Entity
@Table(name = "leave_policy")
public class LeavePolicy extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    /** Unused days that may move to next year. */
    @Column(name = "carry_forward_limit", nullable = false)
    private int carryForwardLimit;

    @Column(name = "min_days_per_application", nullable = false)
    private int minDaysPerApplication = 1;

    @Column(name = "max_days_per_application", nullable = false)
    private int maxDaysPerApplication = 30;

    /** false = applications are approved as soon as they are made. */
    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
