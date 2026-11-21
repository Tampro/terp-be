package com.terp.erp.employee.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

/** company_id null = system type (seeded: Identity Proof, Address Proof, Educational Certificates), read-only. */
@Getter
@Setter
@Entity
@Table(name = "document_type")
public class DocumentType extends BaseAuditableEntity {
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Company type: the company's choice. System type: default until a company sets DocumentTypeSetting. */
    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSlot;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
