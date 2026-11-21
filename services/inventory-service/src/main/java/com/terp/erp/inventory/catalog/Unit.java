package com.terp.erp.inventory.catalog;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Unit of measure (pcs, kg, hour …). */
@Getter
@Setter
@Entity
@Table(name = "product_unit")
public class Unit extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
