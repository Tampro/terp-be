package com.terp.erp.order.cart;

import com.terp.erp.order.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The cart of one membership (member-service customer id) at one store. */
@Getter
@Setter
@Entity
@Table(name = "cart")
public class Cart extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "bag_requested", nullable = false)
    private boolean bagRequested;
}
