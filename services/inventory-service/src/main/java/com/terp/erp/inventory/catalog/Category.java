package com.terp.erp.inventory.catalog;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Product/service category (ERP manual 9 / 8.3). */
@Getter
@Setter
@Entity
@Table(name = "product_category")
public class Category extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
