package com.terp.erp.order.coupon;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.ECouponError;
import com.terp.erp.order.coupon.CouponDtos.CouponDto;
import com.terp.erp.order.coupon.CouponDtos.CouponForm;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/** Back Office coupons of the caller's company (COUPON_VIEW / COUPON_MANAGE). */
@Service
@RequiredArgsConstructor
public class CouponService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CouponRepository coupons;

    @Transactional(readOnly = true)
    public Page<CouponDto> list(Long requestedCompanyId, Boolean active, String q, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, ECouponError.CPN_006);
        String query = q == null || q.isBlank() ? null : q.trim();
        return coupons.search(company, active, query, pageable).map(CouponDto::of);
    }

    @Transactional(readOnly = true)
    public CouponDto get(Long id) {
        return CouponDto.of(require(id));
    }

    @Transactional
    public CouponDto create(Long requestedCompanyId, CouponForm f) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, ECouponError.CPN_006);
        check(f);
        String code = CouponRules.normalize(f.code());
        if (coupons.findByCompanyIdAndCodeAndDeletedFalse(company, code).isPresent()) {
            throw ApiException.badRequest(ECouponError.CPN_001);
        }
        Coupon c = new Coupon();
        c.setCompanyId(company);
        c.setCode(code);
        fill(c, f);
        return save(c);
    }

    /** Full replacement; the code of a coupon already used stays. */
    @Transactional
    public CouponDto update(Long id, CouponForm f) {
        Coupon c = require(id);
        check(f);
        String code = CouponRules.normalize(f.code());
        if (!code.equals(c.getCode())) {
            if (c.getUsedCount() > 0) {
                throw ApiException.badRequest(ECouponError.CPN_003);
            }
            coupons.findByCompanyIdAndCodeAndDeletedFalse(c.getCompanyId(), code)
                    .filter(o -> !Objects.equals(o.getId(), c.getId()))
                    .ifPresent(o -> {
                        throw ApiException.badRequest(ECouponError.CPN_001);
                    });
            c.setCode(code);
        }
        fill(c, f);
        return save(c);
    }

    /** An unused coupon is soft-deleted (its code free again); a used one is only switched off (CPN_004). */
    @Transactional
    public void delete(Long id) {
        Coupon c = require(id);
        if (c.getUsedCount() > 0) {
            throw ApiException.badRequest(ECouponError.CPN_004);
        }
        c.setDeleted(true);
        c.setDeletedKey(c.getId());
        coupons.save(c);
    }

    private CouponDto save(Coupon c) {
        try {
            return CouponDto.of(coupons.saveAndFlush(c));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest(ECouponError.CPN_001);
        }
    }

    private static void check(CouponForm f) {
        if (f.type() == ECouponType.PERCENT && f.value().compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest(ECouponError.CPN_002, "a percentage is at most 100");
        }
        if (!f.endsAt().isAfter(f.startsAt())) {
            throw ApiException.badRequest(ECouponError.CPN_002, "endsAt must be after startsAt");
        }
    }

    private static void fill(Coupon c, CouponForm f) {
        c.setName(f.name().trim());
        c.setType(f.type());
        c.setValue(f.value());
        c.setMaxDiscount(f.maxDiscount());
        c.setMinSubtotal(f.minSubtotal());
        c.setStartsAt(f.startsAt());
        c.setEndsAt(f.endsAt());
        c.setTotalLimit(f.totalLimit());
        c.setPerCustomerLimit(f.perCustomerLimit());
        c.setActive(f.active());
    }

    private Coupon require(Long id) {
        Coupon c = coupons.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECouponError.CPN_005));
        TenantGuard.assertSameCompany(c.getCompanyId(), ECouponError.CPN_005);
        return c;
    }
}
