package com.terp.erp.inventory.transfer;

import com.terp.erp.inventory.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "stock_transfer")
public class StockTransfer extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "from_warehouse_id", nullable = false)
    private Long fromWarehouseId;

    @Column(name = "to_warehouse_id", nullable = false)
    private Long toWarehouseId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
