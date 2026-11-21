package com.terp.erp.crm.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

/** Where a lead or deal came from (web, referral, …); company-wide, not per pipeline. */
@Getter
@Setter
@Entity
@Table(name = "crm_source")
public class Source extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
