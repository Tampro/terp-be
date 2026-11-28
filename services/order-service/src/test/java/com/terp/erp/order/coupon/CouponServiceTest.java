package com.terp.erp.order.coupon;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.order.common.ECouponError;
import com.terp.erp.order.coupon.CouponDtos.CouponForm;
import com.terp.erp.security.ErpPrincipal;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponServiceTest {
    private final CouponRepository coupons = mock(CouponRepository.class);
    private final CouponService service = new CouponService(coupons);
    private final Coupon used = CouponRulesTest.coupon(ECouponType.AMOUNT, "1000");

    @BeforeEach
    void setUp() {
        ErpPrincipal p = new ErpPrincipal(5L, "kc-5", "u5", 10L, false, Set.of(), Set.of("COUPON_MANAGE"));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "kc-5"));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "kc-5");
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
        used.setCompanyId(10L);
        used.setUsedCount(3);
        when(coupons.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(used));
        when(coupons.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CouponForm form(String code, ECouponType type, String value, Instant start, Instant end) {
        return new CouponForm(code, "Welcome", type, new BigDecimal(value), null, null, start, end, null, null, true);
    }

    private static CouponForm ok(String code) {
        return form(code, ECouponType.PERCENT, "10", CouponRulesTest.NOW, CouponRulesTest.NOW.plusSeconds(60));
    }

    @Test
    void createUpperCasesAndRefusesDuplicatesAndBadValues() {
        when(coupons.findByCompanyIdAndCodeAndDeletedFalse(10L, "WELCOME10")).thenReturn(Optional.empty());
        assertThat(service.create(null, ok("welcome10")).code()).isEqualTo("WELCOME10");
        when(coupons.findByCompanyIdAndCodeAndDeletedFalse(10L, "WELCOME10")).thenReturn(Optional.of(used));
        assertCode(() -> service.create(null, ok("Welcome10")), ECouponError.CPN_001);
        assertCode(() -> service.create(null, form("X1", ECouponType.PERCENT, "101", CouponRulesTest.NOW,
                CouponRulesTest.NOW.plusSeconds(60))), ECouponError.CPN_002);
        assertCode(() -> service.create(null, form("X2", ECouponType.AMOUNT, "1", CouponRulesTest.NOW,
                CouponRulesTest.NOW)), ECouponError.CPN_002);
    }

    @Test
    void aUsedCouponKeepsItsCodeAndCannotBeDeleted() {
        assertCode(() -> service.update(1L, ok("OTHER")), ECouponError.CPN_003);
        assertThat(service.update(1L, ok("welcome10")).name()).isEqualTo("Welcome");
        assertCode(() -> service.delete(1L), ECouponError.CPN_004);
        used.setUsedCount(0);
        service.delete(1L);
        assertThat(used.isDeleted()).isTrue();
        assertThat(used.getDeletedKey()).isEqualTo(1L);
    }

    @Test
    void anotherCompanysCouponIsNotFound() {
        used.setCompanyId(11L);
        assertCode(() -> service.get(1L), ECouponError.CPN_005);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
