package com.terp.erp.document.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;

/** A company policy or form (docs/analysis/06 §9.4.1). */
@Getter
@Setter
@Entity
@Table(name = "hr_document")
public class HrDocument extends BaseAuditableEntity {
    /**
     * FR-ERP-DOC-001: Pending → Approved / Rejected. PENDING and REJECTED were added next to the earlier stages, which
     * stay valid for Update Status (no data migration needed).
     * TODO(assumption): DRAFT / UNDER_REVIEW are kept as pre-approval stages and PUBLISHED / ARCHIVED / EXPIRED as
     * post-approval ones; only PENDING can be approved or rejected — confirm with BA.
     */
    public enum EStatus { DRAFT, PENDING, UNDER_REVIEW, APPROVED, REJECTED, PUBLISHED, ARCHIVED, EXPIRED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    /** A media library file of the company. */
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "requires_acknowledgment", nullable = false)
    private boolean requiresAcknowledgment;

    /** As set by the admin; see HrDocumentService for the effective (expired) status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    /** Approve / Reject: Keycloak sub and username of who decided, when, and the optional comment. */
    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_by_name", length = 200)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
