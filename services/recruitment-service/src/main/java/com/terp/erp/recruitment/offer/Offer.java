package com.terp.erp.recruitment.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A job offer to a candidate (docs/analysis/06 §7.1.4). */
@Getter
@Setter
@Entity
@Table(name = "offer")
public class Offer extends BaseAuditableEntity {
    /** Kanban column order. */
    public enum EStatus { DRAFT, SENT, NEGOTIATING, ACCEPTED, DECLINED, EXPIRED }

    /** Separate from the status (user manual §17: "plus approval Approved/Rejected"). */
    public enum EApprovalStatus { PENDING, APPROVED, REJECTED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    /** The position offered. */
    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal salary;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "approved_by_employee_id")
    private Long approvedByEmployeeId;

    @Column(name = "approved_by_name", length = 200)
    private String approvedByName;

    @Column(name = "benefits", length = 5000)
    private String benefits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 10)
    private EApprovalStatus approvalStatus = EApprovalStatus.PENDING;

    /** Keycloak subject of the user who approved / rejected; approvedByEmployeeId stays the designated approver. */
    @Column(name = "approval_decided_by", length = 64)
    private String approvalDecidedBy;

    @Column(name = "approval_decided_at")
    private Instant approvalDecidedAt;

    @Column(name = "offer_date", nullable = false)
    private LocalDate offerDate;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
