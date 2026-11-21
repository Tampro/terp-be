package com.terp.erp.document.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;

import java.time.LocalDate;

/** A company policy or form (docs/analysis/06 §9.4.1). */
@Getter
@Setter
@Entity
@Table(name = "hr_document")
public class HrDocument extends BaseAuditableEntity {
    public enum EStatus { DRAFT, UNDER_REVIEW, APPROVED, PUBLISHED, ARCHIVED, EXPIRED }

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

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
