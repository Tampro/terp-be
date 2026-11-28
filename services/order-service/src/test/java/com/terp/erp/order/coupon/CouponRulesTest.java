package com.terp.erp.order.coupon;

import com.terp.erp.order.common.ECouponError;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CouponRulesTest {
    static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");

    static Coupon coupon(ECouponType type, String value) {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setCode("WELCOME10");
        c.setType(type);
        c.setValue(new BigDecimal(value));
        c.setStartsAt(NOW.minusSeconds(3600));
        c.setEndsAt(NOW.plusSeconds(3600));
        c.setActive(true);
        return c;
    }

    private static CouponRules.Result apply(Coupon c, String subtotal, long uses) {
        return CouponRules.apply(c, new BigDecimal(subtotal), NOW, uses);
    }

    @Test
    void percentIsRoundedDownAndCapped() {
        Coupon c = coupon(ECouponType.PERCENT, "15");
        assertThat(apply(c, "10999", 0).discount()).isEqualByComparingTo("1649");
        c.setMaxDiscount(new BigDecimal("1000"));
        assertThat(apply(c, "10999", 0).discount()).isEqualByComparingTo("1000");
    }

    @Test
    void anAmountNeverExceedsTheGoods() {
        assertThat(apply(coupon(ECouponType.AMOUNT, "3000"), "10000", 0).discount()).isEqualByComparingTo("3000");
        assertThat(apply(coupon(ECouponType.AMOUNT, "30000"), "10000", 0).discount()).isEqualByComparingTo("10000");
    }

    @Test
    void refusals() {
        Coupon off = coupon(ECouponType.AMOUNT, "1000");
        off.setActive(false);
        assertThat(apply(off, "10000", 0).error()).isEqualTo(ECouponError.CPN_007);
        assertThat(CouponRules.apply(null, BigDecimal.TEN, NOW, 0).error()).isEqualTo(ECouponError.CPN_007);
        Coupon later = coupon(ECouponType.AMOUNT, "1000");
        later.setStartsAt(NOW.plusSeconds(1));
        assertThat(apply(later, "10000", 0).error()).isEqualTo(ECouponError.CPN_008);
        Coupon ended = coupon(ECouponType.AMOUNT, "1000");
        ended.setEndsAt(NOW);                                                    // end exclusive
        assertThat(apply(ended, "10000", 0).error()).isEqualTo(ECouponError.CPN_008);
        Coupon min = coupon(ECouponType.AMOUNT, "1000");
        min.setMinSubtotal(new BigDecimal("20000"));
        CouponRules.Result r = apply(min, "19999", 0);
        assertThat(r.error()).isEqualTo(ECouponError.CPN_009);
        assertThat(r.arg()).isEqualTo("20000");
        Coupon total = coupon(ECouponType.AMOUNT, "1000");
        total.setTotalLimit(1);
        total.setUsedCount(1);
        assertThat(apply(total, "10000", 0).error()).isEqualTo(ECouponError.CPN_010);
        Coupon mine = coupon(ECouponType.AMOUNT, "1000");
        mine.setPerCustomerLimit(1);
        assertThat(apply(mine, "10000", 1).error()).isEqualTo(ECouponError.CPN_011);
        assertThat(apply(mine, "10000", 0).ok()).isTrue();
    }
}
