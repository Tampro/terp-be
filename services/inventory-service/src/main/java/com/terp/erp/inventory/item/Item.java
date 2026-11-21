package com.terp.erp.inventory.item;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A product, service or part sold or bought by the company (TERP Core product_service_items). */
@Getter
@Setter
@Entity
@Table(name = "item")
public class Item extends BaseAuditableEntity {
    /** SERVICE has no stock; PRODUCT and PART are stocked per warehouse. */
    public enum EItemType {
        PRODUCT, SERVICE, PART;

        public boolean stocked() {
            return this != SERVICE;
        }
    }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private EItemType type;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "tax_id")
    private Long taxId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice = BigDecimal.ZERO;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
