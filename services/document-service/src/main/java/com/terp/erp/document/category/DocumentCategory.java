package com.terp.erp.document.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;
import com.terp.erp.document.common.EStatus;

/** A classification of HR documents (docs/analysis/06 §9.4.7). */
@Getter
@Setter
@Entity
@Table(name = "document_category")
public class DocumentCategory extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#3B82F6";

    /** An icon key of the front end (folder, shield, user, …). */
    @Column(name = "icon", nullable = false, length = 30)
    private String icon = "folder";

    /** Documents of a mandatory category require acknowledgment by default. */
    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
