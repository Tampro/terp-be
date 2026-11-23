package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.saas.SaasDtos.PlanDto;
import com.terp.erp.iam.saas.SaasDtos.PlanForm;
import com.terp.erp.iam.security.LocalAuthorityResolver;

import java.math.BigDecimal;
import java.util.List;

/** Plans, managed by the super admin (coupons: CouponService). */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final PlanRepository plans;
    private final LocalAuthorityResolver authorityCache;

    @Transactional(readOnly = true)
    public List<PlanDto> allPlans() {
        return plans.findByDeletedFalseOrderByMonthlyPriceAsc().stream().map(PlanDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanDto> offeredPlans() {
        return plans.findByEnabledTrueAndDeletedFalseOrderByMonthlyPriceAsc().stream().map(PlanDto::of).toList();
    }

    @Transactional
    public PlanDto createPlan(PlanForm form) {
        if (plans.existsByCodeAndDeletedFalse(form.code())) {
            throw ApiException.badRequest(ESaasError.SAAS_002);
        }
        Plan p = new Plan();
        apply(p, form);
        return PlanDto.of(plans.save(p));
    }

    /** Full replacement. Limit changes apply to every company on the plan (within the 30 s authority cache). */
    @Transactional
    public PlanDto updatePlan(Long id, PlanForm form) {
        Plan p = requirePlan(id);
        if (!p.getCode().equals(form.code()) && plans.existsByCodeAndDeletedFalse(form.code())) {
            throw ApiException.badRequest(ESaasError.SAAS_002);
        }
        // Unsetting the default is allowed (no default = expired companies are blocked); a default plan is always
        // enabled (apply() forces it).
        apply(p, form);
        authorityCache.evictAll();
        return PlanDto.of(p);
    }

    /** Companies on a deleted plan fall back to the default plan (or EXPIRED) at their next request. */
    @Transactional
    public void deletePlan(Long id) {
        Plan p = requirePlan(id);
        if (p.isDefaultPlan()) {
            throw ApiException.badRequest(ESaasError.SAAS_013);
        }
        p.setDeleted(true);
        authorityCache.evictAll();
    }

    /** Active / Inactive toggle on the plan card. Companies already on an inactive plan keep it until it ends. */
    @Transactional
    public PlanDto setEnabled(Long id, boolean enabled) {
        Plan p = requirePlan(id);
        if (!enabled && p.isDefaultPlan()) {
            throw ApiException.badRequest(ESaasError.SAAS_015);
        }
        p.setEnabled(enabled);
        return PlanDto.of(p);
    }

    /** Plan by id including deleted ones — for showing names on old orders. */
    public Plan findAny(Long id) {
        return plans.findById(id).orElse(null);
    }

    public Plan requirePlan(Long id) {
        return plans.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESaasError.SAAS_001));
    }

    private void apply(Plan p, PlanForm f) {
        if (f.free() && (f.monthlyPrice().signum() != 0
                || (f.yearlyPrice() != null && f.yearlyPrice().signum() != 0))) {
            throw ApiException.badRequest(ESaasError.SAAS_041);
        }
        if (f.defaultPlan()) {
            // Only one default: the previous one stops being it.
            plans.findFirstByDefaultPlanTrueAndDeletedFalse()
                    .filter(d -> !d.getId().equals(p.getId()))
                    .ifPresent(d -> d.setDefaultPlan(false));
        }
        p.setCode(f.code());
        p.setName(f.name());
        p.setDescription(f.description());
        p.setMonthlyPrice(f.monthlyPrice());
        p.setYearlyPrice(f.yearlyPrice());
        p.setMaxUsers(f.maxUsers());
        p.setMaxEmployees(f.maxEmployees());
        p.setStorageLimitGb(f.storageLimitGb());
        p.setTrialDays(f.trialDays());
        p.setAiIntegration(f.aiIntegration());
        p.setRecommended(f.recommended());
        p.setFree(f.free());
        p.setBadge(f.badge() == null || f.badge().isBlank() ? null : f.badge().trim());
        p.setDefaultPlan(f.defaultPlan());
        p.setEnabled(f.enabled() || f.defaultPlan());
        p.getModules().clear();
        if (f.modules() != null) {
            p.getModules().addAll(f.modules());
        }
    }

    /** Every module a plan can include, in a stable order, for the plan form and the comparison table. */
    public List<SaasDtos.PlanModuleDto> moduleCatalogue() {
        return java.util.Arrays.stream(EPlanModule.values()).map(SaasDtos.PlanModuleDto::of).toList();
    }
}
