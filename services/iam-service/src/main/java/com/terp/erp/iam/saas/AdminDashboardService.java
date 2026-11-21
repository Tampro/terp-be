package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Super Admin Dashboard (docs/analysis/06 §3.1). Months are UTC.
 * TODO(assumption): revenue = final price of APPROVED plan orders, dated by their approval (processedAt, else the
 * order date); an active company = ACTIVE status with a TRIAL or ACTIVE subscription.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final CompanyRepository companies;
    private final PlanRepository plans;
    private final PlanOrderRepository orders;
    private final PlanRequestRepository requests;
    private final UserAccountRepository users;
    private final SubscriptionService subscriptions;
    private final Clock clock;

    public record RecentCompany(String id, String name, String email, Instant createdDate, ECompanyStatus status) {
    }

    public record TopPlan(String planId, String name, long subscribers, BigDecimal revenue) {
    }

    /**
     * monthlyGrowthPercent: companies created this month vs last month, null when last month had none.
     * newCompaniesByMonth / revenueByMonth: 12 values (Jan..Dec) of year.
     */
    public record AdminDashboard(BigDecimal totalRevenue, long totalCompanies, long activeCompanies, long activePlans,
                                 long totalUsers, long pendingRequests, Double monthlyGrowthPercent,
                                 List<RecentCompany> recentCompanies, List<TopPlan> topPlans, int year,
                                 List<Long> newCompaniesByMonth, List<BigDecimal> revenueByMonth) {
    }

    @Transactional(readOnly = true)
    public AdminDashboard dashboard(Integer year) {
        YearMonth now = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        int y = year == null ? now.getYear() : year;
        List<Company> all = companies.findAllByDeletedFalse();
        List<PlanOrder> approved = orders.findByStatus(EOrderStatus.APPROVED);
        Map<Long, Plan> livePlans = plans.findByDeletedFalseOrderByMonthlyPriceAsc().stream()
                .collect(Collectors.toMap(Plan::getId, Function.identity()));

        long active = all.stream().filter(c -> c.getStatus() == ECompanyStatus.ACTIVE)
                .filter(c -> Set.of(ESubscriptionStatus.TRIAL, ESubscriptionStatus.ACTIVE)
                        .contains(subscriptions.effective(c).status()))
                .count();
        long thisMonth = all.stream().filter(c -> month(c.getCreatedDate()).equals(now)).count();
        long lastMonth = all.stream().filter(c -> month(c.getCreatedDate()).equals(now.minusMonths(1))).count();
        Double growth = lastMonth == 0 ? null
                : BigDecimal.valueOf((thisMonth - lastMonth) * 100.0 / lastMonth).setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        List<RecentCompany> recent = all.stream()
                .sorted(Comparator.comparing(Company::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(c -> new RecentCompany(c.getId().toString(), c.getName(), c.getEmail(), c.getCreatedDate(),
                        c.getStatus()))
                .toList();

        Map<Long, BigDecimal> revenueByPlan = new HashMap<>();
        approved.forEach(o -> revenueByPlan.merge(o.getPlanId(), price(o), BigDecimal::add));
        Map<Long, Long> subscribers = all.stream().filter(c -> c.getPlanId() != null)
                .collect(Collectors.groupingBy(Company::getPlanId, Collectors.counting()));
        List<TopPlan> top = livePlans.values().stream()
                .map(p -> new TopPlan(p.getId().toString(), p.getName(), subscribers.getOrDefault(p.getId(), 0L),
                        revenueByPlan.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(TopPlan::revenue).reversed()
                        .thenComparing(Comparator.comparingLong(TopPlan::subscribers).reversed()))
                .limit(5)
                .toList();

        List<Long> newByMonth = new ArrayList<>();
        List<BigDecimal> revenueByMonth = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(y, m);
            newByMonth.add(all.stream().filter(c -> month(c.getCreatedDate()).equals(ym)).count());
            revenueByMonth.add(approved.stream().filter(o -> month(approvedAt(o)).equals(ym)).map(AdminDashboardService::price)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        BigDecimal total = approved.stream().map(AdminDashboardService::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        long activePlans = livePlans.values().stream().filter(Plan::isEnabled).count();
        long pending = requests.countByStatus(EPlanRequestStatus.PENDING) + orders.countByStatus(EOrderStatus.PENDING);
        return new AdminDashboard(total, all.size(), active, activePlans, users.countByDeletedFalse(), pending, growth,
                recent, top, y, newByMonth, revenueByMonth);
    }

    private static Instant approvedAt(PlanOrder o) {
        return o.getProcessedAt() != null ? o.getProcessedAt() : o.getCreatedDate();
    }

    private static BigDecimal price(PlanOrder o) {
        return o.getFinalPrice() == null ? BigDecimal.ZERO : o.getFinalPrice();
    }

    private static YearMonth month(Instant t) {
        return t == null ? YearMonth.of(1970, 1) : YearMonth.from(t.atZone(ZoneOffset.UTC));
    }
}
