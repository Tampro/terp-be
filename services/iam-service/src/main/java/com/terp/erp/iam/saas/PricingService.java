package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Coupon;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.CouponRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** The one place an order price is computed (quote and order use the same code). */
@Service
@RequiredArgsConstructor
public class PricingService {
    /** Orders that consume a coupon use; a rejected or cancelled order gives it back. */
    private static final List<EOrderStatus> COUNTED = List.of(EOrderStatus.PENDING, EOrderStatus.APPROVED);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CouponRepository coupons;
    private final PlanOrderRepository orders;
    private final Clock clock;

    public record Quote(BigDecimal originalPrice, BigDecimal discountAmount, BigDecimal finalPrice, Coupon coupon) {
    }

    public Quote quote(Plan plan, EBillingCycle cycle, String couponCode, Long companyId) {
        BigDecimal original = cycle == EBillingCycle.YEARLY ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        if (original == null) {
            throw ApiException.badRequest(ESaasError.SAAS_003);
        }
        original = original.setScale(2, RoundingMode.HALF_UP);
        if (couponCode == null || couponCode.isBlank()) {
            return new Quote(original, BigDecimal.ZERO.setScale(2), original, null);
        }
        Coupon c = coupons.findByCodeAndDeletedFalse(couponCode.trim())
                .orElseThrow(() -> ApiException.badRequest(ESaasError.SAAS_006));
        assertUsable(c, companyId);
        if (c.getMinimumSpend() != null && original.compareTo(c.getMinimumSpend()) < 0) {
            throw ApiException.badRequest(ESaasError.SAAS_007);
        }
        if (c.getMaximumSpend() != null && original.compareTo(c.getMaximumSpend()) > 0) {
            throw ApiException.badRequest(ESaasError.SAAS_019);
        }
        BigDecimal discount = c.getType() == ECouponType.PERCENTAGE
                ? original.multiply(c.getDiscountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : c.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        discount = discount.min(original); // never below zero
        return new Quote(original, discount, original.subtract(discount), c);
    }

    private void assertUsable(Coupon c, Long companyId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        boolean expired = c.getExpiryDate() != null && today.isAfter(c.getExpiryDate());
        boolean totalUsed = c.getUseLimitTotal() != null
                && orders.countByCouponIdAndStatusIn(c.getId(), COUNTED) >= c.getUseLimitTotal();
        boolean companyUsed = c.getUseLimitPerCompany() != null
                && orders.countByCouponIdAndCompanyIdAndStatusIn(c.getId(), companyId, COUNTED)
                >= c.getUseLimitPerCompany();
        if (!c.isEnabled() || expired || totalUsed || companyUsed) {
            throw ApiException.badRequest(ESaasError.SAAS_006);
        }
    }
}
