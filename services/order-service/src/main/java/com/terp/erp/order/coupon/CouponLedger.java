package com.terp.erp.order.coupon;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.ECouponError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Coupon uses for orders: preview (no lock), reserve under a row lock in the order's transaction, release. */
@Component
@RequiredArgsConstructor
public class CouponLedger {
    private final CouponRepository coupons;
    private final CouponUseRepository uses;
    private final Clock clock;

    /** For the cart preview: never locks, never fails — the refusal comes back as the result's error. */
    @Transactional(readOnly = true)
    public CouponRules.Result preview(long companyId, long customerId, String code, BigDecimal subtotal) {
        Optional<Coupon> c = coupons.findByCompanyIdAndCodeAndDeletedFalse(companyId, CouponRules.normalize(code));
        return CouponRules.apply(c.orElse(null), subtotal, clock.instant(),
                c.map(x -> uses.countByCouponIdAndCustomerId(x.getId(), customerId)).orElse(0L));
    }

    /** The locked coupon and its discount, or a 400 CPN_0xx; the caller then {@link #reserve}s it for its order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Applied lockAndCheck(long companyId, long customerId, String code, BigDecimal subtotal) {
        Coupon c = coupons.lockLive(companyId, CouponRules.normalize(code)).orElse(null);
        CouponRules.Result r = CouponRules.apply(c, subtotal, clock.instant(),
                c == null ? 0 : uses.countByCouponIdAndCustomerId(c.getId(), customerId));
        if (!r.ok()) {
            throw r.arg() == null ? ApiException.badRequest(r.error()) : ApiException.badRequest(r.error(), r.arg());
        }
        return new Applied(c, r.discount());
    }

    public record Applied(Coupon coupon, BigDecimal discount) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Coupon c, long customerId, long orderId) {
        CouponUse u = new CouponUse();
        u.setCouponId(c.getId());
        u.setCustomerId(customerId);
        u.setOrderId(orderId);
        u.setCreatedDate(Instant.now(clock));
        uses.save(u);
        c.setUsedCount(c.getUsedCount() + 1);
        coupons.save(c);
    }

    /** Gives back the use an order held (cancelled / expired); nothing when it held none. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long orderId) {
        uses.findByOrderId(orderId).ifPresent(u -> {
            coupons.lockById(u.getCouponId()).ifPresent(c -> {
                c.setUsedCount(Math.max(0, c.getUsedCount() - 1));
                coupons.save(c);
            });
            uses.delete(u);
        });
    }

    static ApiException notFound() {
        return ApiException.notFound(ECouponError.CPN_005);
    }
}
