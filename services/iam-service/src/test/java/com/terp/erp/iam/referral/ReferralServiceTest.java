package com.terp.erp.iam.referral;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.saas.EPaymentMethod;
import com.terp.erp.iam.saas.ESaasError;
import com.terp.erp.security.ErpPrincipal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralServiceTest {
    private ReferralCommissionRepository commissions;
    private PayoutRequestRepository payouts;
    private CompanyRepository companies;
    private ReferralService service;
    private ReferralSetting setting;

    @BeforeEach
    void setUp() {
        ReferralSettingRepository settings = mock(ReferralSettingRepository.class);
        commissions = mock(ReferralCommissionRepository.class);
        payouts = mock(PayoutRequestRepository.class);
        companies = mock(CompanyRepository.class);
        service = new ReferralService(settings, commissions, payouts, companies, mock(PlanRepository.class),
                Clock.systemUTC(), mock(com.terp.erp.iam.email.EmailNotifier.class));
        setting = new ReferralSetting();
        setting.setId(1L);
        setting.setEnabled(true);
        setting.setCommissionPercentage(new BigDecimal("10.00"));
        setting.setThresholdAmount(new BigDecimal("500000"));
        when(settings.findById(1L)).thenReturn(Optional.of(setting));
        when(payouts.save(any(PayoutRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        Company me = new Company();
        me.setId(10L);
        me.setName("Referrer Co");
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(me));
        login(10L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void codeIsRefPlusSixDigits() {
        assertThat(ReferralService.codeFor(7L)).isEqualTo("REF000007");
    }

    @Test
    void unknownReferralCodeIsRefused() {
        when(companies.findByReferralCodeAndDeletedFalse("REF999999")).thenReturn(Optional.empty());
        assertError(() -> service.referrerOf("ref999999"), ESaasError.SAAS_026);
        assertThat(service.referrerOf("  ")).isNull();
    }

    @Test
    void approvedBankTransferEarnsFrozenPercentageOfFinalPrice() {
        service.onOrderApproved(order(EPaymentMethod.BANK_TRANSFER, "792000"), referred());
        ArgumentCaptor<ReferralCommission> c = ArgumentCaptor.forClass(ReferralCommission.class);
        verify(commissions).save(c.capture());
        assertThat(c.getValue().getAmount()).isEqualByComparingTo("79200.00");
        assertThat(c.getValue().getReferrerCompanyId()).isEqualTo(10L);
        assertThat(c.getValue().getCommissionPercentage()).isEqualByComparingTo("10");
    }

    @Test
    void manualFreeOrUnreferredOrdersEarnNothing() {
        service.onOrderApproved(order(EPaymentMethod.MANUAL, "990000"), referred());
        service.onOrderApproved(order(EPaymentMethod.BANK_TRANSFER, "0"), referred());
        Company notReferred = referred();
        notReferred.setReferredByCompanyId(null);
        service.onOrderApproved(order(EPaymentMethod.BANK_TRANSFER, "990000"), notReferred);
        setting.setEnabled(false);
        service.onOrderApproved(order(EPaymentMethod.BANK_TRANSFER, "990000"), referred());
        verify(commissions, never()).save(any());
    }

    @Test
    void payoutRulesThresholdBalanceAndOnePending() {
        when(commissions.totalEarned(10L)).thenReturn(new BigDecimal("800000"));
        when(payouts.sumByCompanyAndStatus(eq(10L), anyCollection())).thenReturn(new BigDecimal("200000"));

        assertError(() -> service.requestPayout(new BigDecimal("400000")), ESaasError.SAAS_027);
        assertError(() -> service.requestPayout(new BigDecimal("700000")), ESaasError.SAAS_028);
        assertThat(service.requestPayout(new BigDecimal("600000")).status()).isEqualTo(EPayoutStatus.PENDING);

        when(payouts.existsByCompanyIdAndStatus(10L, EPayoutStatus.PENDING)).thenReturn(true);
        assertError(() -> service.requestPayout(new BigDecimal("500000")), ESaasError.SAAS_029);

        setting.setEnabled(false);
        assertError(() -> service.requestPayout(new BigDecimal("500000")), ESaasError.SAAS_032);
    }

    @Test
    void onlyPendingPayoutCanBeDecided() {
        PayoutRequest p = new PayoutRequest();
        p.setId(3L);
        p.setCompanyId(10L);
        p.setStatus(EPayoutStatus.APPROVED);
        when(payouts.findById(3L)).thenReturn(Optional.of(p));
        assertError(() -> service.decide(3L, false, null), ESaasError.SAAS_031);
        verify(payouts, never()).sumByStatus(any());
        assertThat(List.of(p.getStatus())).containsExactly(EPayoutStatus.APPROVED);
    }

    private static Company referred() {
        Company c = new Company();
        c.setId(20L);
        c.setReferredByCompanyId(10L);
        return c;
    }

    private static PlanOrder order(EPaymentMethod method, String finalPrice) {
        PlanOrder o = new PlanOrder();
        o.setId(99L);
        o.setPaymentMethod(method);
        o.setFinalPrice(new BigDecimal(finalPrice));
        return o;
    }

    private static void login(Long companyId) {
        ErpPrincipal p = new ErpPrincipal(5L, "kc-5", "u5", companyId, false, Set.of("COMPANY_ADMIN"),
                Set.of("SAAS_ORDER_CREATE"));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "kc-5"));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "kc-5");
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static void assertError(Runnable call, ESaasError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
