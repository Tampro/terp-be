package com.terp.erp.employee.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

/** A company's Required/Optional choice for a system document type; system rows themselves stay read-only. */
@Getter
@Setter
@Entity
@Table(name = "document_type_setting")
public class DocumentTypeSetting extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Column(name = "required", nullable = false)
    private boolean required;
}
