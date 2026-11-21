package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Coupon;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.repository.CouponRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;
import com.terp.erp.iam.saas.CouponDtos.CouponCountsDto;
import com.terp.erp.iam.saas.CouponDtos.CouponDetailDto;
import com.terp.erp.iam.saas.CouponDtos.CouponDto;
import com.terp.erp.iam.saas.CouponDtos.CouponForm;
import com.terp.erp.iam.saas.CouponDtos.CouponUsageDto;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Coupons page (docs/analysis/06 §3.2.4), super admin only. Pricing with a coupon lives in PricingService. */
@Service
@RequiredArgsConstructor
public class CouponService {
    /** Same statuses PricingService counts toward the limits. */
    static final List<EOrderStatus> COUNTED = List.of(EOrderStatus.PENDING, EOrderStatus.APPROVED);
    // No 0/O/1/I: auto codes are read aloud and typed by hand.
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CouponRepository coupons;
    private final PlanOrderRepository orders;

    /**
     * @param status ACTIVE / INACTIVE tab; null = All
     * @param from   first created day included (UTC)
     * @param to     last created day included (UTC)
     */
    @Transactional(readOnly = true)
    public Page<CouponDto> search(String keyword, ECouponType type, LocalDate from, LocalDate to, Boolean status,
                                  Pageable pageable) {
        Page<Coupon> page = coupons.search(OrderService.kw(keyword), type, OrderService.start(from),
                OrderService.end(to), status, pageable);
        Map<Long, Long> uses = page.isEmpty() ? Map.of()
                : orders.countUsesByCoupon(page.map(Coupon::getId).getContent(), COUNTED).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return page.map(c -> CouponDto.of(c, uses.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public CouponCountsDto counts(String keyword, ECouponType type, LocalDate from, LocalDate to) {
        Map<Boolean, Long> m = coupons.countByEnabled(OrderService.kw(keyword), type, OrderService.start(from),
                OrderService.end(to)).stream().collect(Collectors.toMap(r -> (Boolean) r[0], r -> (Long) r[1]));
        long active = m.getOrDefault(true, 0L), inactive = m.getOrDefault(false, 0L);
        return new CouponCountsDto(active + inactive, active, inactive);
    }

    /** View Details: summary + information + every order that used the coupon (any status, newest first). */
    @Transactional(readOnly = true)
    public CouponDetailDto detail(Long id) {
        Coupon c = require(id);
        List<CouponUsageDto> usage = orders.usageOfCoupon(c.getId()).stream().map(r -> {
            PlanOrder o = (PlanOrder) r[0];
            return new CouponUsageDto(SaasDtos.str(o.getId()), o.getOrderNumber(), SaasDtos.str(o.getCompanyId()),
                    (String) r[1], (String) r[2], o.getOriginalPrice(), o.getDiscountAmount(), o.getFinalPrice(),
                    o.getStatus(), o.getCreatedDate());
        }).toList();
        long used = usage.stream().filter(u -> COUNTED.contains(u.status())).count();
        return new CouponDetailDto(CouponDto.of(c, used), usage);
    }

    @Transactional
    public CouponDto create(CouponForm form) {
        Coupon c = new Coupon();
        if (form.codeGeneration() == ECouponCodeType.AUTO) {
            c.setCode(uniqueCode());
            c.setCodeType(ECouponCodeType.AUTO);
        } else {
            c.setCode(requireManualCode(form));
            c.setCodeType(ECouponCodeType.MANUAL);
        }
        apply(c, form);
        return CouponDto.of(coupons.save(c), 0);
    }

    /** Full replacement. AUTO keeps the current code; MANUAL takes the sent one. */
    @Transactional
    public CouponDto update(Long id, CouponForm form) {
        Coupon c = require(id);
        if (form.codeGeneration() != ECouponCodeType.AUTO) {
            String code = form.code();
            if (code == null || code.isBlank()) {
                throw ApiException.badRequest(ESaasError.SAAS_021);
            }
            if (!c.getCode().equals(code) && coupons.existsByCodeAndDeletedFalse(code)) {
                throw ApiException.badRequest(ESaasError.SAAS_005);
            }
            c.setCode(code);
            c.setCodeType(ECouponCodeType.MANUAL);
        }
        apply(c, form);
        return CouponDto.of(c, usesOf(c));
    }

    @Transactional
    public CouponDto setEnabled(Long id, boolean enabled) {
        Coupon c = require(id);
        c.setEnabled(enabled);
        return CouponDto.of(c, usesOf(c));
    }

    /**
     * Soft delete: orders keep their reference (and their frozen discount); the code becomes free again.
     * TODO(assumption): the reference manual says "permanently removes" — kept soft because paid orders point here.
     */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private Coupon require(Long id) {
        return coupons.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_004));
    }

    private long usesOf(Coupon c) {
        return orders.countByCouponIdAndStatusIn(c.getId(), COUNTED);
    }

    private String requireManualCode(CouponForm form) {
        String code = form.code();
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest(ESaasError.SAAS_021);
        }
        if (coupons.existsByCodeAndDeletedFalse(code)) {
            throw ApiException.badRequest(ESaasError.SAAS_005);
        }
        return code;
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (!coupons.existsByCodeAndDeletedFalse(sb.toString())) {
                return sb.toString();
            }
        }
        // 32^8 codes: ten collisions in a row means something else is wrong.
        throw new IllegalStateException("Could not generate a unique coupon code");
    }

    static void apply(Coupon c, CouponForm f) {
        if (f.type() == ECouponType.PERCENTAGE && f.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest(ESaasError.SAAS_012);
        }
        if (f.minimumSpend() != null && f.maximumSpend() != null && f.minimumSpend().compareTo(f.maximumSpend()) > 0) {
            throw ApiException.badRequest(ESaasError.SAAS_020);
        }
        c.setName(f.name());
        c.setType(f.type());
        c.setDiscountValue(f.discountValue());
        c.setMinimumSpend(f.minimumSpend());
        c.setMaximumSpend(f.maximumSpend());
        c.setUseLimitTotal(f.useLimitTotal());
        c.setUseLimitPerCompany(f.useLimitPerCompany());
        c.setExpiryDate(f.expiryDate());
        c.setEnabled(f.enabled() == null || f.enabled());
    }
}
