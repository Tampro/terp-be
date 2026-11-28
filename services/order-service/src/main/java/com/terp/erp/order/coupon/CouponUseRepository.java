package com.terp.erp.order.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponUseRepository extends JpaRepository<CouponUse, Long> {
    long countByCouponIdAndCustomerId(Long couponId, Long customerId);

    Optional<CouponUse> findByOrderId(Long orderId);
}
