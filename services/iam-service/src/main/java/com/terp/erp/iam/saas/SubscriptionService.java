package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.repository.UserAccountRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * Subscription lifecycle of a company (docs/analysis/01 §5 state diagram, adapted):
 * <pre>
 * new company ──▶ default plan (TRIAL for trialDays if > 0, else ACTIVE without expiry)
 * TRIAL/ACTIVE ──expires──▶ default plan, if one exists and it is not the plan that just expired
 *                       └──▶ EXPIRED otherwise: employees blocked, COMPANY_ADMIN may only renew
 * approved order ──▶ ACTIVE on the ordered plan for 1 month / 1 year (extends when renewing the same plan)
 * no plan (bootstrap/internal company) ──▶ NONE: no limits, never expires
 * </pre>
 * The fallback is computed on read, not written: the effective plan is decided here every time.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final PlanRepository plans;
    private final UserAccountRepository users;
    private final Clock clock;

    /** What applies to the company right now. {@code plan} is null for NONE and EXPIRED. */
    public record Effective(ESubscriptionStatus status, Plan plan, Instant expiresAt, boolean fallback) {
        public boolean blocked() {
            return status == ESubscriptionStatus.EXPIRED;
        }
    }

    @Transactional(readOnly = true)
    public Effective effective(Company company) {
        if (company.getPlanId() == null) {
            return new Effective(ESubscriptionStatus.NONE, null, null, false);
        }
        return effective(company, plans.findByIdAndDeletedFalse(company.getPlanId()),
                () -> plans.findFirstByDefaultPlanTrueAndDeletedFalse());
    }

    /**
     * Same rule for a page of companies without one query per row: the caller loads the live plans once.
     *
     * @param livePlans every non-deleted plan by id
     */
    public Effective effective(Company company, java.util.Map<Long, Plan> livePlans) {
        if (company.getPlanId() == null) {
            return new Effective(ESubscriptionStatus.NONE, null, null, false);
        }
        return effective(company, Optional.ofNullable(livePlans.get(company.getPlanId())),
                () -> livePlans.values().stream().filter(Plan::isDefaultPlan).findFirst());
    }

    private Effective effective(Company company, Optional<Plan> current,
                                java.util.function.Supplier<Optional<Plan>> defaultPlan) {
        Instant now = clock.instant();
        boolean expired = company.getPlanExpiresAt() != null && !company.getPlanExpiresAt().isAfter(now);
        if (current.isPresent() && !expired) {
            return new Effective(company.isOnTrial() ? ESubscriptionStatus.TRIAL : ESubscriptionStatus.ACTIVE,
                    current.get(), company.getPlanExpiresAt(), false);
        }
        // Expired (or its plan was deleted): fall back to the default plan — but not to the plan whose trial just
        // ended, or a trial would never end.
        Optional<Plan> fallback = defaultPlan.get()
                .filter(d -> !Objects.equals(d.getId(), company.getPlanId()));
        return fallback.map(d -> new Effective(ESubscriptionStatus.ACTIVE, d, null, true))
                .orElseGet(() -> new Effective(ESubscriptionStatus.EXPIRED, null, company.getPlanExpiresAt(), false));
    }

    /** Who may still log in to an EXPIRED company, to renew it: COMPANY_ADMIN only (PO, 2026-11-18). */
    public static boolean mayRenew(java.util.Set<String> roleCodes) {
        return roleCodes.contains(com.terp.erp.iam.constant.EDefaultRole.COMPANY_ADMIN.name());
    }

    /** At company creation. No default plan configured → the company has no subscription (NONE). */
    public void assignDefault(Company company) {
        plans.findFirstByDefaultPlanTrueAndDeletedFalse().ifPresent(d -> {
            company.setPlanId(d.getId());
            company.setOnTrial(d.getTrialDays() > 0);
            company.setPlanExpiresAt(d.getTrialDays() > 0
                    ? clock.instant().plus(java.time.Duration.ofDays(d.getTrialDays())) : null);
        });
    }

    /**
     * Activates a paid period. Renewing the plan that is still running adds to its end date; anything else
     * (another plan, a trial, an expired plan) starts now.
     */
    public void activate(Company company, Plan plan, EBillingCycle cycle) {
        Instant now = clock.instant();
        boolean extendCurrent = Objects.equals(company.getPlanId(), plan.getId()) && !company.isOnTrial()
                && company.getPlanExpiresAt() != null && company.getPlanExpiresAt().isAfter(now);
        Instant start = extendCurrent ? company.getPlanExpiresAt() : now;
        var startDate = start.atZone(ZoneOffset.UTC);
        Instant end = (cycle == EBillingCycle.YEARLY ? startDate.plusYears(1) : startDate.plusMonths(1)).toInstant();
        company.setPlanId(plan.getId());
        company.setPlanExpiresAt(end);
        company.setOnTrial(false);
    }

    /**
     * Start Trial (docs/analysis/06 §10.2.1, PO 2026-11-21): an enabled plan with trial days, once per company and
     * plan, not while a paid period runs (it would be replaced). When the trial ends the default plan applies.
     */
    public void startTrial(Company company, Plan plan, boolean triedBefore) {
        Instant now = clock.instant();
        if (plan.getTrialDays() <= 0) {
            throw ApiException.badRequest(ESaasError.SAAS_034);
        }
        boolean trialingThis = Objects.equals(company.getPlanId(), plan.getId()) && company.isOnTrial();
        if (triedBefore || trialingThis) {
            throw ApiException.badRequest(ESaasError.SAAS_035);
        }
        if (!company.isOnTrial() && company.getPlanExpiresAt() != null && company.getPlanExpiresAt().isAfter(now)) {
            throw ApiException.badRequest(ESaasError.SAAS_036,
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(company.getPlanExpiresAt().atZone(ZoneOffset.UTC)));
        }
        company.setPlanId(plan.getId());
        company.setOnTrial(true);
        company.setPlanExpiresAt(now.plus(java.time.Duration.ofDays(plan.getTrialDays())));
    }

    /** Super admin override: any plan, any end (null = no expiry). */
    public void assign(Company company, Plan plan, Instant expiresAt) {
        company.setPlanId(plan.getId());
        company.setPlanExpiresAt(expiresAt);
        company.setOnTrial(false);
    }

    /** Called before an account is added to the company. */
    @Transactional(readOnly = true)
    public void assertCanAddUser(Company company) {
        Effective e = effective(company);
        if (e.plan() == null || e.plan().getMaxUsers() == null) {
            return;
        }
        if (users.countByCompanyIdAndDeletedFalse(company.getId()) >= e.plan().getMaxUsers()) {
            throw ApiException.badRequest(ESaasError.SAAS_011, e.plan().getMaxUsers());
        }
    }
}
