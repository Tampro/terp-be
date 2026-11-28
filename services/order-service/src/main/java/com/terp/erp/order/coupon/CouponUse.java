package com.terp.erp.order.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A use held by an order (one per order); deleted when the order is cancelled or expires. */
@Getter
@Setter
@Entity
@Table(name = "coupon_use")
public class CouponUse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "coupon_id", nullable = false) private Long couponId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "created_date", nullable = false) private Instant createdDate;
}
