package com.terp.erp.order.cart;

import com.terp.erp.order.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An item and its quantity in a cart; never a price (read live). */
@Getter
@Setter
@Entity
@Table(name = "cart_line")
public class CartLine extends BaseAuditableEntity {
    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Exempt from the 90-day clean-up ("계속보관"). */
    @Column(name = "keep_line", nullable = false)
    private boolean keep;
}
