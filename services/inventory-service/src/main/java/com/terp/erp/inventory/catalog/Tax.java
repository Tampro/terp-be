package com.terp.erp.inventory.catalog;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A tax applied to items; rate in percent (0..100). */
@Getter
@Setter
@Entity
@Table(name = "product_tax")
public class Tax extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
