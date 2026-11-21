package com.terp.erp.document.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;
import com.terp.erp.document.common.EStatus;

/** A contract or document template with {{variable}} placeholders (docs/analysis/06 §9.4.4, §9.4.5). */
@Getter
@Setter
@Entity
@Table(name = "doc_template")
public class DocTemplate extends BaseAuditableEntity {
    public enum EKind { CONTRACT, DOCUMENT }

    /** Only PDF for now (PO, 2026-11-21). */
    public enum EFormat { PDF }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private EKind kind;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "contract_type_id")
    private Long contractTypeId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 10)
    private EFormat fileFormat = EFormat.PDF;

    /** The form's Draft = INACTIVE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** Comma-separated variable names, in order. */
    @Column(name = "variables", length = 4000)
    private String variables;

    /** JSON object of default values (document templates). */
    @Column(name = "default_values", columnDefinition = "TEXT")
    private String defaultValues;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
