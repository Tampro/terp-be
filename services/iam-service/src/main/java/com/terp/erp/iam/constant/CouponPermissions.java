package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Coupon permissions checked by order-service (Back Office coupons of Express orders); must match the Liquibase seed
 * 20261129180100_coupon_permission_init_data. Module ORDER is always on (EPlanModule.ALWAYS_ON).
 */
public final class CouponPermissions {
    public static final String COUPON_VIEW = "COUPON_VIEW";
    public static final String COUPON_MANAGE = "COUPON_MANAGE";
    public static final Set<String> ALL = Set.of(COUPON_VIEW, COUPON_MANAGE);

    private CouponPermissions() {
    }
}
