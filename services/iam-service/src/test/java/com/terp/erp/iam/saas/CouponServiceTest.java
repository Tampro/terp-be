package com.terp.erp.iam.saas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Coupon;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.repository.CouponRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;
import com.terp.erp.iam.saas.CouponDtos.CouponForm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponServiceTest {
    private CouponRepository coupons;
    private PlanOrderRepository orders;
    private CouponService service;

    @BeforeEach
    void setUp() {
        coupons = mock(CouponRepository.class);
        orders = mock(PlanOrderRepository.class);
        service = new CouponService(coupons, orders);
        when(coupons.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
    }

    private static CouponForm form(ECouponCodeType gen, String code, String min, String max) {
        return new CouponForm("Summer", ECouponType.PERCENTAGE, new BigDecimal("20"), gen, code,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), 100, 1, null, null, null);
    }

    @Test
    void autoGenerationMakesAnUnambiguousEightCharacterCode() {
        when(coupons.existsByCodeAndDeletedFalse(anyString())).thenReturn(false);
        var dto = service.create(form(ECouponCodeType.AUTO, null, null, null));
        assertThat(dto.code()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(dto.codeGeneration()).isEqualTo(ECouponCodeType.AUTO);
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void manualEntryNeedsAFreeCode() {
        assertError(() -> service.create(form(ECouponCodeType.MANUAL, null, null, null)), ESaasError.SAAS_021);
        when(coupons.existsByCodeAndDeletedFalse("SUMMER25")).thenReturn(true);
        assertError(() -> service.create(form(null, "SUMMER25", null, null)), ESaasError.SAAS_005);
    }

    @Test
    void minimumSpendAboveMaximumIsRefused() {
        assertError(() -> service.create(form(ECouponCodeType.MANUAL, "X1", "500", "100")), ESaasError.SAAS_020);
    }

    @Test
    void autoCodeIsKeptOnUpdate() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setCode("ABCD2345");
        c.setCodeType(ECouponCodeType.AUTO);
        when(coupons.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(c));
        assertThat(service.update(1L, form(ECouponCodeType.AUTO, "IGNORED", null, null)).code()).isEqualTo("ABCD2345");
    }

    @Test
    void timesUsedCountsOnlyPendingAndApprovedOrders() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setCode("X");
        when(coupons.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(c));
        when(orders.usageOfCoupon(1L)).thenReturn(List.of(
                row(EOrderStatus.APPROVED), row(EOrderStatus.PENDING), row(EOrderStatus.REJECTED)));

        var detail = service.detail(1L);
        assertThat(detail.usageHistory()).hasSize(3);
        assertThat(detail.coupon().timesUsed()).isEqualTo(2);
    }

    private static Object[] row(EOrderStatus status) {
        PlanOrder o = new PlanOrder();
        o.setId(9L);
        o.setCompanyId(10L);
        o.setStatus(status);
        return new Object[]{o, "Test Co", "co@example.invalid"};
    }

    private static void assertError(Runnable call, ESaasError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
