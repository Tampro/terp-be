package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanRequest;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanRequestRepository;
import com.terp.erp.iam.saas.SaasDtos.PlanRequestDto;
import com.terp.erp.iam.saas.SaasDtos.PlanRequestForm;
import com.terp.erp.iam.saas.SaasDtos.StatusCountsDto;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;

/**
 * Plan Requests (docs/analysis/06 §3.2.3): a company asks for a plan and billing cycle; a super admin approves
 * (plan activated for one cycle, recorded as an APPROVED MANUAL order) or rejects. One pending request per company.
 */
@Service
@RequiredArgsConstructor
public class PlanRequestService {
    private final PlanRequestRepository requests;
    private final CompanyRepository companies;
    private final CatalogService catalog;
    private final OrderService orders;
    private final Clock clock;
    private final com.terp.erp.iam.email.EmailNotifier notifier;

    // ------------------------------------------------------------------ company side

    @Transactional
    public PlanRequestDto create(PlanRequestForm form) {
        Long companyId = TenantGuard.resolveCompany(null, EIamError.IAM_003);
        if (requests.existsByCompanyIdAndStatus(companyId, EPlanRequestStatus.PENDING)) {
            throw ApiException.badRequest(ESaasError.SAAS_018);
        }
        Plan plan = catalog.requirePlan(form.planId());
        if (!plan.isEnabled()) {
            throw ApiException.badRequest(ESaasError.SAAS_014);
        }
        if (form.billingCycle() == EBillingCycle.YEARLY && plan.getYearlyPrice() == null) {
            throw ApiException.badRequest(ESaasError.SAAS_003);
        }
        PlanRequest r = new PlanRequest();
        r.setCompanyId(companyId);
        r.setPlanId(plan.getId());
        r.setBillingCycle(form.billingCycle());
        r.setMessage(form.message());
        r.setStatus(EPlanRequestStatus.PENDING);
        return dto(requests.save(r), plan);
    }

    @Transactional(readOnly = true)
    public Page<PlanRequestDto> mine(Long requestedCompanyId, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return requests.findByCompanyId(companyId, pageable).map(r -> dto(r, catalog.findAny(r.getPlanId())));
    }

    @Transactional
    public PlanRequestDto cancel(Long id) {
        PlanRequest r = require(id);
        decide(r, EPlanRequestStatus.CANCELLED, null);
        return dto(r, catalog.findAny(r.getPlanId()));
    }

    // ------------------------------------------------------------------ super admin side

    @Transactional(readOnly = true)
    public Page<PlanRequestDto> search(String keyword, EPlanRequestStatus status, Pageable pageable) {
        return requests.searchWithNames(OrderService.kw(keyword), status, pageable)
                .map(row -> PlanRequestDto.of((PlanRequest) row[0], (String) row[1], (String) row[2], (String) row[3]));
    }

    @Transactional(readOnly = true)
    public StatusCountsDto counts(String keyword) {
        return StatusCountsDto.of(requests.countByStatus(OrderService.kw(keyword)));
    }

    /** "Approves and activates the requested plan for the company". */
    @Transactional
    public PlanRequestDto approve(Long id, String notes) {
        PlanRequest r = require(id);
        decide(r, EPlanRequestStatus.APPROVED, notes);
        var order = orders.activateManually(r.getCompanyId(), r.getPlanId(), r.getBillingCycle(),
                "Plan request #" + r.getId());
        r.setOrderId(Long.valueOf(order.id()));
        notifyDecision(true, r, notes);
        return dto(r, catalog.findAny(r.getPlanId()));
    }

    @Transactional
    public PlanRequestDto reject(Long id, String notes) {
        PlanRequest r = require(id);
        decide(r, EPlanRequestStatus.REJECTED, notes);
        notifyDecision(false, r, notes);
        return dto(r, catalog.findAny(r.getPlanId()));
    }

    private void notifyDecision(boolean approved, PlanRequest r, String notes) {
        companies.findById(r.getCompanyId()).ifPresent(c -> {
            Plan p = catalog.findAny(r.getPlanId());
            notifier.planRequestDecided(approved, c, p == null ? "" : p.getName(), r.getBillingCycle().name(), notes);
        });
    }

    // ------------------------------------------------------------------ helpers

    private void decide(PlanRequest r, EPlanRequestStatus to, String notes) {
        if (r.getStatus() != EPlanRequestStatus.PENDING) {
            throw ApiException.badRequest(ESaasError.SAAS_017);
        }
        r.setStatus(to);
        r.setProcessedAt(clock.instant());
        r.setProcessedBy(CurrentUser.get().keycloakId());
        if (notes != null) {
            r.setNotes(notes);
        }
    }

    private PlanRequest require(Long id) {
        PlanRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_016));
        TenantGuard.assertSameCompany(r.getCompanyId(), ESaasError.SAAS_016);
        return r;
    }

    private PlanRequestDto dto(PlanRequest r, Plan plan) {
        Company c = companies.findById(r.getCompanyId()).orElse(null);
        return PlanRequestDto.of(r, c == null ? null : c.getName(), c == null ? null : c.getEmail(),
                plan == null ? null : plan.getName());
    }
}
