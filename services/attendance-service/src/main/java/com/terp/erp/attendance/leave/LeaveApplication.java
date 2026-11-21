package com.terp.erp.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A leave request (docs/analysis/06 §6.5.1). totalDays = working days of [startDate, endDate] when it was saved.
 * Approving it uses the balance and marks the attendance calendar ON_LEAVE (PO, 2026-11-19).
 */
@Getter
@Setter
@Entity
@Table(name = "leave_application")
public class LeaveApplication extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "leave_policy_id")
    private Long leavePolicyId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "attachment_file_id")
    private Long attachmentFileId;

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
