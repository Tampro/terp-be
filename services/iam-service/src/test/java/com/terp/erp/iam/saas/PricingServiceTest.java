package com.terp.erp.iam.saas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Coupon;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.CouponRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {
    private CouponRepository coupons;
    private PlanOrderRepository orders;
    private PricingService pricing;
    private Plan plan;

    @BeforeEach
    void setUp() {
        coupons = mock(CouponRepository.class);
        orders = mock(PlanOrderRepository.class);
        pricing = new PricingService(coupons, orders, Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
        plan = new Plan();
        plan.setMonthlyPrice(new BigDecimal("490000"));
        plan.setYearlyPrice(new BigDecimal("4900000"));
    }

    @Test
    void noCouponMeansFullPrice() {
        var q = pricing.quote(plan, EBillingCycle.YEARLY, null, 10L);
        assertThat(q.finalPrice()).isEqualByComparingTo("4900000");
        assertThat(q.discountAmount()).isEqualByComparingTo("0");
    }

    @Test
    void percentageCoupon() {
        coupon("TEN", ECouponType.PERCENTAGE, "10");
        var q = pricing.quote(plan, EBillingCycle.MONTHLY, "TEN", 10L);
        assertThat(q.discountAmount()).isEqualByComparingTo("49000");
        assertThat(q.finalPrice()).isEqualByComparingTo("441000");
    }

    @Test
    void flatCouponNeverMakesThePriceNegative() {
        coupon("BIG", ECouponType.FLAT, "1000000");
        assertThat(pricing.quote(plan, EBillingCycle.MONTHLY, "BIG", 10L).finalPrice()).isEqualByComparingTo("0");
    }

    @Test
    void planWithoutYearlyPriceCannotBeBoughtYearly() {
        plan.setYearlyPrice(null);
        assertError(() -> pricing.quote(plan, EBillingCycle.YEARLY, null, 10L), ESaasError.SAAS_003);
    }

    @Test
    void expiredDisabledOrUsedUpCouponsAreRefused() {
        Coupon c = coupon("OLD", ECouponType.PERCENTAGE, "10");
        c.setExpiryDate(LocalDate.parse("2026-11-17"));
        assertError(() -> pricing.quote(plan, EBillingCycle.MONTHLY, "OLD", 10L), ESaasError.SAAS_006);

        c.setExpiryDate(LocalDate.parse("2026-11-18")); // last valid day is inclusive
        pricing.quote(plan, EBillingCycle.MONTHLY, "OLD", 10L);

        c.setUseLimitPerCompany(1);
        when(orders.countByCouponIdAndCompanyIdAndStatusIn(anyLong(), eq(10L), any())).thenReturn(1L);
        assertError(() -> pricing.quote(plan, EBillingCycle.MONTHLY, "OLD", 10L), ESaasError.SAAS_006);

        c.setUseLimitPerCompany(null);
        c.setEnabled(false);
        assertError(() -> pricing.quote(plan, EBillingCycle.MONTHLY, "OLD", 10L), ESaasError.SAAS_006);
    }

    @Test
    void minimumSpendIsChecked() {
        coupon("MIN", ECouponType.FLAT, "10000").setMinimumSpend(new BigDecimal("1000000"));
        assertError(() -> pricing.quote(plan, EBillingCycle.MONTHLY, "MIN", 10L), ESaasError.SAAS_007);
    }

    @Test
    void maximumSpendIsChecked() {
        coupon("MAX", ECouponType.FLAT, "10000").setMaximumSpend(new BigDecimal("1000000"));
        assertThat(pricing.quote(plan, EBillingCycle.MONTHLY, "MAX", 10L).finalPrice()).isEqualByComparingTo("480000");
        assertError(() -> pricing.quote(plan, EBillingCycle.YEARLY, "MAX", 10L), ESaasError.SAAS_019);
    }

    private Coupon coupon(String code, ECouponType type, String value) {
        Coupon c = new Coupon();
        c.setId(7L);
        c.setCode(code);
        c.setType(type);
        c.setDiscountValue(new BigDecimal(value));
        c.setEnabled(true);
        when(coupons.findByCodeAndDeletedFalse(code)).thenReturn(Optional.of(c));
        return c;
    }

    private static void assertError(Runnable call, ESaasError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
