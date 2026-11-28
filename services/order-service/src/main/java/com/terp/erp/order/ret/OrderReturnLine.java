package com.terp.erp.order.ret;

import com.terp.erp.order.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "order_return_line")
public class OrderReturnLine extends BaseAuditableEntity {
    @Column(name = "return_id", nullable = false) private Long returnId;
    @Column(name = "order_line_id", nullable = false) private Long orderLineId;
    @Column(name = "item_id", nullable = false) private Long itemId;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;
    /** What the customer paid for these units: goods after the coupon and points, spread pro rata. */
    @Column(name = "share", nullable = false, precision = 19, scale = 4) private BigDecimal share;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4) private BigDecimal unitCost;
}
