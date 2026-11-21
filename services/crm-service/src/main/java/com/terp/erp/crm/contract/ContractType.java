package com.terp.erp.crm.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

/** A company's kind of contract (ERP manual 7.5 "Contract types: name, status"). */
@Getter
@Setter
@Entity
@Table(name = "contract_type")
public class ContractType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
