package com.terp.erp.iam.referral;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.referral.ReferralDtos.CommissionDto;
import com.terp.erp.iam.referral.ReferralDtos.DashboardDto;
import com.terp.erp.iam.referral.ReferralDtos.MyReferralDto;
import com.terp.erp.iam.referral.ReferralDtos.PayoutCountsDto;
import com.terp.erp.iam.referral.ReferralDtos.PayoutDto;
import com.terp.erp.iam.referral.ReferralDtos.ReferredCompanyDto;
import com.terp.erp.iam.referral.ReferralDtos.ReferredSummaryDto;
import com.terp.erp.iam.referral.ReferralDtos.SettingsDto;
import com.terp.erp.iam.referral.ReferralDtos.SettingsForm;
import com.terp.erp.iam.referral.ReferralDtos.TopReferrerDto;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.saas.EPaymentMethod;
import com.terp.erp.iam.saas.ESaasError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Referral program (docs/analysis/06 §3.2.6; PO decisions 2026-11-19):
 * <ul>
 *   <li>a company is referred when a referral code is given at its creation;</li>
 *   <li>every APPROVED BANK_TRANSFER order with a price of the referred company earns its referrer
 *       {@code percentage × final price} (MANUAL orders bring no money and earn nothing), percentage frozen;</li>
 *   <li>a COMPANY_ADMIN of the referrer asks for a payout (≥ threshold, ≤ available balance, one pending);
 *       the super admin approves (paid outside the system) or rejects (the amount returns to the balance).</li>
 * </ul>
 * Available balance = commission earned − pending payouts − approved payouts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {
    private static final List<EPayoutStatus> RESERVED = List.of(EPayoutStatus.PENDING, EPayoutStatus.APPROVED);

    private final ReferralSettingRepository settings;
    private final ReferralCommissionRepository commissions;
    private final PayoutRequestRepository payouts;
    private final CompanyRepository companies;
    private final PlanRepository plans;
    private final Clock clock;
    private final com.terp.erp.iam.email.EmailNotifier notifier;

    // ------------------------------------------------------------------ hooks used by companies and orders

    /** REF + 6-digit id, the same rule the migration used for existing companies. */
    public static String codeFor(Long companyId) {
        return String.format("REF%06d", companyId);
    }

    /** Referrer of a new company, from the code given at creation; null when none was given. */
    @Transactional(readOnly = true)
    public Long referrerOf(String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return null;
        }
        return companies.findByReferralCodeAndDeletedFalse(referralCode.trim().toUpperCase())
                .map(Company::getId)
                .orElseThrow(() -> ApiException.badRequest(ESaasError.SAAS_026));
    }

    /** Called when an order is approved, in the same transaction. */
    @Transactional
    public void onOrderApproved(PlanOrder order, Company company) {
        ReferralSetting s = setting();
        if (!s.isEnabled() || company.getReferredByCompanyId() == null
                || order.getPaymentMethod() != EPaymentMethod.BANK_TRANSFER
                || order.getFinalPrice().signum() <= 0 || commissions.existsByOrderId(order.getId())) {
            return;
        }
        ReferralCommission c = new ReferralCommission();
        c.setReferrerCompanyId(company.getReferredByCompanyId());
        c.setReferredCompanyId(company.getId());
        c.setOrderId(order.getId());
        c.setOrderAmount(order.getFinalPrice());
        c.setCommissionPercentage(s.getCommissionPercentage());
        c.setAmount(order.getFinalPrice().multiply(s.getCommissionPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        commissions.save(c);
    }

    // ------------------------------------------------------------------ company side

    @Transactional(readOnly = true)
    public MyReferralDto mine(Long requestedCompanyId) {
        Company c = company(TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003));
        ReferralSetting s = setting();
        BigDecimal earned = commissions.totalEarned(c.getId());
        BigDecimal paid = payouts.sumByCompanyAndStatus(c.getId(), List.of(EPayoutStatus.APPROVED));
        BigDecimal pending = payouts.sumByCompanyAndStatus(c.getId(), List.of(EPayoutStatus.PENDING));
        return new MyReferralDto(c.getReferralCode(), s.isEnabled(), s.getCommissionPercentage(),
                s.getThresholdAmount(), s.getGuidelines(), companies.countReferredBy(c.getId()), earned, paid, pending,
                earned.subtract(paid).subtract(pending));
    }

    @Transactional
    public PayoutDto requestPayout(BigDecimal amount) {
        Company c = company(TenantGuard.resolveCompany(null, EIamError.IAM_003));
        ReferralSetting s = setting();
        if (!s.isEnabled()) {
            throw ApiException.badRequest(ESaasError.SAAS_032);
        }
        if (payouts.existsByCompanyIdAndStatus(c.getId(), EPayoutStatus.PENDING)) {
            throw ApiException.badRequest(ESaasError.SAAS_029);
        }
        if (amount.compareTo(s.getThresholdAmount()) < 0) {
            throw ApiException.badRequest(ESaasError.SAAS_027, s.getThresholdAmount().toPlainString());
        }
        BigDecimal available = commissions.totalEarned(c.getId())
                .subtract(payouts.sumByCompanyAndStatus(c.getId(), RESERVED));
        if (amount.compareTo(available) > 0) {
            throw ApiException.badRequest(ESaasError.SAAS_028, available.toPlainString());
        }
        PayoutRequest p = new PayoutRequest();
        p.setCompanyId(c.getId());
        p.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        p.setStatus(EPayoutStatus.PENDING);
        return PayoutDto.of(payouts.save(p), c.getName(), c.getEmail());
    }

    @Transactional(readOnly = true)
    public Page<PayoutDto> myPayouts(Long requestedCompanyId, Pageable pageable) {
        Company c = company(TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003));
        return payouts.findByCompanyId(c.getId(), pageable).map(p -> PayoutDto.of(p, c.getName(), c.getEmail()));
    }

    // ------------------------------------------------------------------ super admin

    @Transactional(readOnly = true)
    public SettingsDto settings() {
        return SettingsDto.of(setting());
    }

    /** Applies to orders approved from now on; earned commission keeps its frozen percentage. */
    @Transactional
    public SettingsDto saveSettings(SettingsForm f) {
        ReferralSetting s = setting();
        s.setEnabled(f.enabled());
        s.setCommissionPercentage(f.commissionPercentage());
        s.setThresholdAmount(f.thresholdAmount());
        s.setGuidelines(f.guidelines());
        s.setUpdatedBy(CurrentUser.get().keycloakId());
        s.setUpdatedDate(clock.instant());
        return SettingsDto.of(s);
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        Instant yearStart = Year.now(clock).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TopReferrerDto> top = commissions.topReferrers(PageRequest.of(0, 10)).stream().map(r -> {
            Company c = (Company) r[0];
            return new TopReferrerDto(ReferralDtos.str(c.getId()), c.getName(), c.getEmail(), (Long) r[1],
                    (BigDecimal) r[2]);
        }).toList();
        return new DashboardDto(companies.countReferred(), payouts.sumByStatus(EPayoutStatus.PENDING),
                payouts.sumByStatus(EPayoutStatus.APPROVED), companies.countReferredActive(), top,
                companies.countReferredSince(yearStart),
                payouts.sumProcessedSince(EPayoutStatus.APPROVED, yearStart));
    }

    @Transactional(readOnly = true)
    public ReferredSummaryDto referredSummary() {
        return new ReferredSummaryDto(companies.countReferred(), companies.countReferredWithPlan(),
                commissions.totalAll());
    }

    /** Referred Users list: one query per kind for the page (companies, referrers, plans, commissions). */
    @Transactional(readOnly = true)
    public Page<ReferredCompanyDto> referred(String keyword, Pageable pageable) {
        Page<Company> page = companies.searchReferred(keyword == null ? "" : keyword.trim(), pageable);
        List<Long> ids = page.map(Company::getId).getContent();
        Map<Long, Company> referrers = companies.findAllById(page.map(Company::getReferredByCompanyId).getContent())
                .stream().collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<Long, Plan> planById = plans.findAllById(page.map(Company::getPlanId).filter(java.util.Objects::nonNull)
                .toList()).stream().collect(Collectors.toMap(Plan::getId, Function.identity()));
        Map<Long, List<ReferralCommission>> history = ids.isEmpty() ? Map.of()
                : commissions.findByReferredCompanyIdInOrderByIdDesc(ids).stream()
                .collect(Collectors.groupingBy(ReferralCommission::getReferredCompanyId));
        return page.map(c -> {
            Company ref = referrers.get(c.getReferredByCompanyId());
            Plan plan = c.getPlanId() == null ? null : planById.get(c.getPlanId());
            List<ReferralCommission> h = history.getOrDefault(c.getId(), List.of());
            return new ReferredCompanyDto(ReferralDtos.str(c.getId()), c.getName(), c.getEmail(), c.getCreatedDate(),
                    ReferralDtos.str(c.getReferredByCompanyId()), ref == null ? null : ref.getName(),
                    plan == null ? null : plan.getName(), plan == null ? null : plan.getMonthlyPrice(),
                    h.stream().map(ReferralCommission::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    h.stream().map(CommissionDto::of).toList());
        });
    }

    @Transactional(readOnly = true)
    public Page<PayoutDto> searchPayouts(String keyword, EPayoutStatus status, Pageable pageable) {
        return payouts.searchWithNames(keyword == null ? "" : keyword.trim(), status, pageable)
                .map(r -> PayoutDto.of((PayoutRequest) r[0], (String) r[1], (String) r[2]));
    }

    @Transactional(readOnly = true)
    public PayoutCountsDto payoutCounts(String keyword) {
        Map<EPayoutStatus, Long> m = payouts.countByStatus(keyword == null ? "" : keyword.trim()).stream()
                .collect(Collectors.toMap(r -> (EPayoutStatus) r[0], r -> (Long) r[1]));
        long p = m.getOrDefault(EPayoutStatus.PENDING, 0L), a = m.getOrDefault(EPayoutStatus.APPROVED, 0L),
                rj = m.getOrDefault(EPayoutStatus.REJECTED, 0L);
        return new PayoutCountsDto(p + a + rj, p, a, rj);
    }

    @Transactional
    public PayoutDto decide(Long id, boolean approve, String notes) {
        PayoutRequest p = payouts.findById(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_030));
        if (p.getStatus() != EPayoutStatus.PENDING) {
            throw ApiException.badRequest(ESaasError.SAAS_031);
        }
        p.setStatus(approve ? EPayoutStatus.APPROVED : EPayoutStatus.REJECTED);
        p.setNotes(notes);
        p.setProcessedAt(clock.instant());
        p.setProcessedBy(CurrentUser.get().keycloakId());
        Company c = companies.findById(p.getCompanyId()).orElse(null);
        if (c != null) {
            notifier.payoutDecided(approve, c, p.getAmount(), notes);
        }
        return PayoutDto.of(p, c == null ? null : c.getName(), c == null ? null : c.getEmail());
    }

    // ------------------------------------------------------------------ helpers

    private ReferralSetting setting() {
        return settings.findById(ReferralSetting.ID)
                .orElseThrow(() -> new IllegalStateException("referral_setting row 1 missing (Liquibase seed)"));
    }

    private Company company(Long id) {
        return companies.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EIamError.IAM_001));
    }
}
