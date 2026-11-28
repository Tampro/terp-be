package com.terp.erp.order.coupon;

import com.terp.erp.order.common.ECouponError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Whether a coupon applies to a goods subtotal, and how much it takes off — a pure function, tested apart from any
 * service. Whole won, rounded down (a customer never gets a fraction more than the coupon says).
 */
public final class CouponRules {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CouponRules() {
    }

    /** discount when it applies (error null), else the refusal with its argument (the minimum for CPN_009). */
    public record Result(BigDecimal discount, ECouponError error, Object arg) {
        public boolean ok() {
            return error == null;
        }
    }

    public static Result apply(Coupon c, BigDecimal subtotal, Instant now, long customerUses) {
        if (c == null || c.isDeleted() || !c.isActive()) {
            return refused(ECouponError.CPN_007, null);
        }
        if (now.isBefore(c.getStartsAt()) || !now.isBefore(c.getEndsAt())) {
            return refused(ECouponError.CPN_008, null);
        }
        if (c.getMinSubtotal() != null && subtotal.compareTo(c.getMinSubtotal()) < 0) {
            return refused(ECouponError.CPN_009, c.getMinSubtotal().stripTrailingZeros().toPlainString());
        }
        if (c.getTotalLimit() != null && c.getUsedCount() >= c.getTotalLimit()) {
            return refused(ECouponError.CPN_010, null);
        }
        if (c.getPerCustomerLimit() != null && customerUses >= c.getPerCustomerLimit()) {
            return refused(ECouponError.CPN_011, null);
        }
        BigDecimal d = c.getType() == ECouponType.PERCENT
                ? subtotal.multiply(c.getValue()).divide(HUNDRED, 0, RoundingMode.FLOOR)
                : c.getValue().setScale(0, RoundingMode.FLOOR);
        if (c.getMaxDiscount() != null && d.compareTo(c.getMaxDiscount()) > 0) {
            d = c.getMaxDiscount().setScale(0, RoundingMode.FLOOR);
        }
        if (d.compareTo(subtotal) > 0) {
            d = subtotal;
        }
        return new Result(d, null, null);
    }

    private static Result refused(ECouponError e, Object arg) {
        return new Result(BigDecimal.ZERO, e, arg);
    }

    public static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
