package com.terp.erp.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable ledger line: signed quantity of an item entering (+) or leaving (-) a warehouse, with the document that
 * caused it. Not a BaseAuditableEntity: it is never updated, so it has no updated_* columns.
 */
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "stock_movement")
@EntityListeners(AuditingEntityListener.class)
public class StockMovement {
    /** Source types written by inventory itself; commerce-service sends its own (SALES_INVOICE, SALES_RETURN …). */
    public static final String MANUAL = "MANUAL";
    public static final String OPENING = "OPENING";
    /** source_id = stock_transfer.id; TRANSFER_DELETE lines undo a deleted transfer. */
    public static final String TRANSFER = "TRANSFER";
    public static final String TRANSFER_DELETE = "TRANSFER_DELETE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private Long warehouseId;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "source_type", nullable = false, updatable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate;

    public StockMovement(Long companyId, Long itemId, Long warehouseId, BigDecimal quantity, String sourceType,
                         Long sourceId) {
        this.companyId = companyId;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }
}
