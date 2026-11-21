package com.terp.erp.iam.saas;

import org.junit.jupiter.api.Test;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.repository.PlanRequestRepository;
import com.terp.erp.iam.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardServiceTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanOrderRepository orders = mock(PlanOrderRepository.class);
    private final PlanRequestRepository requests = mock(PlanRequestRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final AdminDashboardService service = new AdminDashboardService(companies, plans, orders, requests, users,
            subscriptions, Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void totalsGrowthTopPlansAndMonths() {
        Plan basic = plan(1L, "Basic", true), pro = plan(2L, "Pro", true), old = plan(3L, "Old", false);
        Company a = company(10L, "2026-11-02T00:00:00Z", 2L, ECompanyStatus.ACTIVE);
        Company b = company(11L, "2026-11-10T00:00:00Z", 1L, ECompanyStatus.ACTIVE);
        Company c = company(12L, "2026-10-05T00:00:00Z", 2L, ECompanyStatus.SUSPENDED);
        when(companies.findAllByDeletedFalse()).thenReturn(List.of(a, b, c));
        when(plans.findByDeletedFalseOrderByMonthlyPriceAsc()).thenReturn(List.of(basic, pro, old));
        when(orders.findByStatus(EOrderStatus.APPROVED)).thenReturn(List.of(
                order(2L, "300", "2026-11-03T00:00:00Z"), order(1L, "100", "2026-10-20T00:00:00Z"),
                order(2L, "50", "2025-12-01T00:00:00Z")));
        when(orders.countByStatus(EOrderStatus.PENDING)).thenReturn(2L);
        when(requests.countByStatus(EPlanRequestStatus.PENDING)).thenReturn(1L);
        when(users.countByDeletedFalse()).thenReturn(9L);
        when(subscriptions.effective(any(Company.class))).thenReturn(
                new SubscriptionService.Effective(ESubscriptionStatus.ACTIVE, null, null, false));

        var d = service.dashboard(null);

        assertThat(d.totalRevenue()).isEqualByComparingTo("450");
        assertThat(d.totalCompanies()).isEqualTo(3);
        assertThat(d.activeCompanies()).isEqualTo(2);
        assertThat(d.activePlans()).isEqualTo(2);
        assertThat(d.totalUsers()).isEqualTo(9);
        assertThat(d.pendingRequests()).isEqualTo(3);
        assertThat(d.monthlyGrowthPercent()).isEqualTo(100.0);
        assertThat(d.recentCompanies()).extracting(rc -> rc.id()).containsExactly("11", "10", "12");
        assertThat(d.topPlans().get(0).name()).isEqualTo("Pro");
        assertThat(d.topPlans().get(0).revenue()).isEqualByComparingTo("350");
        assertThat(d.topPlans().get(0).subscribers()).isEqualTo(2);
        assertThat(d.newCompaniesByMonth().get(10)).isEqualTo(2);
        assertThat(d.newCompaniesByMonth().get(9)).isEqualTo(1);
        assertThat(d.revenueByMonth().get(10)).isEqualByComparingTo("300");
        assertThat(d.revenueByMonth().get(11)).isEqualByComparingTo("0");
    }

    private static Plan plan(Long id, String name, boolean enabled) {
        Plan p = new Plan();
        p.setId(id);
        p.setName(name);
        p.setEnabled(enabled);
        return p;
    }

    private static Company company(Long id, String created, Long planId, ECompanyStatus status) {
        Company c = new Company();
        c.setId(id);
        c.setName("C" + id);
        c.setCreatedDate(Instant.parse(created));
        c.setPlanId(planId);
        c.setStatus(status);
        return c;
    }

    private static PlanOrder order(Long planId, String price, String processed) {
        PlanOrder o = new PlanOrder();
        o.setPlanId(planId);
        o.setFinalPrice(new BigDecimal(price));
        o.setProcessedAt(Instant.parse(processed));
        o.setStatus(EOrderStatus.APPROVED);
        return o;
    }
}
