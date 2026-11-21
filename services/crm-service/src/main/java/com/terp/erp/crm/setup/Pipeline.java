package com.terp.erp.crm.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

/** A sales pipeline of a company; leads and deals move through its stages. */
@Getter
@Setter
@Entity
@Table(name = "crm_pipeline")
public class Pipeline extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
