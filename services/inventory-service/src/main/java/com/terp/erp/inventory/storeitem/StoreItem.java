package com.terp.erp.inventory.storeitem;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Price and on-sale status of an item at one store (warehouse). No live row = not sold there. */
@Getter
@Setter
@Entity
@Table(name = "store_item")
public class StoreItem extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EStoreItemStatus status = EStoreItemStatus.ON_SALE;

    @Column(name = "purchase_limit_qty")
    private Integer purchaseLimitQty;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** 0 while live, the row id once deleted — keeps uk_store_item free for a re-created pair. */
    @Column(name = "deleted_key", nullable = false)
    private long deletedKey;
}
