package com.terp.erp.iam.saas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");

    private PlanRepository plans;
    private UserAccountRepository users;
    private SubscriptionService service;
    private Plan free;
    private Plan pro;

    @BeforeEach
    void setUp() {
        plans = mock(PlanRepository.class);
        users = mock(UserAccountRepository.class);
        service = new SubscriptionService(plans, users, Clock.fixed(NOW, ZoneOffset.UTC));
        free = plan(1L, true, 14, 2);
        pro = plan(2L, false, 0, 50);
        when(plans.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(free));
        when(plans.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(pro));
        when(plans.findFirstByDefaultPlanTrueAndDeletedFalse()).thenReturn(Optional.of(free));
    }

    @Test
    void newCompanyGetsTheDefaultPlanOnTrial() {
        Company c = company(null, null, false);
        service.assignDefault(c);
        assertThat(c.getPlanId()).isEqualTo(1L);
        assertThat(c.isOnTrial()).isTrue();
        assertThat(c.getPlanExpiresAt()).isEqualTo(NOW.plusSeconds(14 * 86400));
        assertThat(service.effective(c).status()).isEqualTo(ESubscriptionStatus.TRIAL);
    }

    @Test
    void endedTrialOfTheDefaultPlanIsExpiredNotRenewedForever() {
        Company c = company(1L, NOW.minusSeconds(1), true);
        assertThat(service.effective(c).blocked()).isTrue();
    }

    @Test
    void expiredPaidPlanFallsBackToTheDefaultPlan() {
        Company c = company(2L, NOW.minusSeconds(1), false);
        var e = service.effective(c);
        assertThat(e.status()).isEqualTo(ESubscriptionStatus.ACTIVE);
        assertThat(e.plan()).isSameAs(free);
        assertThat(e.fallback()).isTrue();
    }

    @Test
    void expiredPaidPlanWithoutDefaultIsBlocked() {
        when(plans.findFirstByDefaultPlanTrueAndDeletedFalse()).thenReturn(Optional.empty());
        assertThat(service.effective(company(2L, NOW.minusSeconds(1), false)).blocked()).isTrue();
    }

    @Test
    void companyWithoutPlanHasNoLimits() {
        Company c = company(null, null, false);
        assertThat(service.effective(c).status()).isEqualTo(ESubscriptionStatus.NONE);
        service.assertCanAddUser(c); // no exception whatever the user count
    }

    @Test
    void renewingTheRunningPlanExtendsFromItsEnd() {
        Instant end = Instant.parse("2026-12-10T00:00:00Z");
        Company c = company(2L, end, false);
        service.activate(c, pro, EBillingCycle.MONTHLY);
        assertThat(c.getPlanExpiresAt()).isEqualTo(Instant.parse("2027-01-10T00:00:00Z"));
    }

    @Test
    void buyingAfterTrialStartsNowAndEndsTheTrial() {
        Company c = company(1L, Instant.parse("2026-11-25T00:00:00Z"), true);
        service.activate(c, pro, EBillingCycle.YEARLY);
        assertThat(c.getPlanId()).isEqualTo(2L);
        assertThat(c.isOnTrial()).isFalse();
        assertThat(c.getPlanExpiresAt()).isEqualTo(Instant.parse("2027-11-18T00:00:00Z"));
    }

    @Test
    void userLimitOfTheEffectivePlanIsEnforced() {
        Company c = company(1L, NOW.plusSeconds(60), true);
        when(users.countByCompanyIdAndDeletedFalse(10L)).thenReturn(2L);
        assertThatThrownBy(() -> service.assertCanAddUser(c))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESaasError.SAAS_011);
    }

    @Test
    void onlyCompanyAdminMayRenewAnExpiredCompany() {
        assertThat(SubscriptionService.mayRenew(java.util.Set.of("COMPANY_ADMIN", "HR"))).isTrue();
        assertThat(SubscriptionService.mayRenew(java.util.Set.of("HR"))).isFalse();
    }

    @Test
    void startTrialOncePerPlanAndNotOverAPaidPeriod() {
        Plan ent = plan(3L, false, 7, 100);
        Company c = company(1L, null, false);
        service.startTrial(c, ent, false);
        assertThat(c.getPlanId()).isEqualTo(3L);
        assertThat(c.isOnTrial()).isTrue();
        assertThat(c.getPlanExpiresAt()).isEqualTo(NOW.plusSeconds(7 * 86400));
        // trialing it now, or tried before
        assertTrial(() -> service.startTrial(c, ent, false), ESaasError.SAAS_035);
        assertTrial(() -> service.startTrial(company(1L, null, false), ent, true), ESaasError.SAAS_035);
        // no trial days
        assertTrial(() -> service.startTrial(company(1L, null, false), pro, false), ESaasError.SAAS_034);
        // a paid period is running
        assertTrial(() -> service.startTrial(company(2L, NOW.plusSeconds(86400), false), ent, false), ESaasError.SAAS_036);
        // another plan's trial may be replaced by this one
        Company onTrial = company(1L, NOW.plusSeconds(86400), true);
        service.startTrial(onTrial, ent, false);
        assertThat(onTrial.getPlanId()).isEqualTo(3L);
    }

    private static void assertTrial(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ESaasError code) {
        org.assertj.core.api.Assertions.assertThatThrownBy(call)
                .extracting(e -> ((com.terp.erp.exception.ApiException) e).getErrorCode()).isEqualTo(code);
    }

    private static Plan plan(Long id, boolean isDefault, int trialDays, Integer maxUsers) {
        Plan p = new Plan();
        p.setId(id);
        p.setCode("P" + id);
        p.setMonthlyPrice(BigDecimal.TEN);
        p.setDefaultPlan(isDefault);
        p.setTrialDays(trialDays);
        p.setMaxUsers(maxUsers);
        p.setEnabled(true);
        return p;
    }

    private static Company company(Long planId, Instant expiresAt, boolean trial) {
        Company c = new Company();
        c.setId(10L);
        c.setPlanId(planId);
        c.setPlanExpiresAt(expiresAt);
        c.setOnTrial(trial);
        return c;
    }
}
