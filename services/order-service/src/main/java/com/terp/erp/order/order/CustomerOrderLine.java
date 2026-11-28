package com.terp.erp.order.order;

import com.terp.erp.order.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A line of an order: name, SKU and unit price as they were at checkout. */
@Getter
@Setter
@Entity
@Table(name = "customer_order_line")
public class CustomerOrderLine extends BaseAuditableEntity {
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "item_id", nullable = false) private Long itemId;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "sku", length = 100) private String sku;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 4) private BigDecimal lineTotal;
}
