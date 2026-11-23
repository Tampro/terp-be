package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A SaaS plan (docs/analysis/01 §5, table `plans`). Limits are null when unlimited. Only one plan may be the
 * default: new companies get it, and companies whose paid plan expired fall back to it.
 */
@Getter
@Setter
@Entity
@Table(name = "plan")
public class Plan extends BaseAuditableEntity {
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "monthly_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyPrice;

    /** Null = no yearly billing for this plan. */
    @Column(name = "yearly_price", precision = 15, scale = 2)
    private BigDecimal yearlyPrice;

    @Column(name = "max_users")
    private Integer maxUsers;

    /** Stored for the employee service; not enforced until that service exists. */
    @Column(name = "max_employees")
    private Integer maxEmployees;

    /** Enforced by media-service on uploads; null or -1 = unlimited. */
    @Column(name = "storage_limit_gb", precision = 10, scale = 2)
    private BigDecimal storageLimitGb;

    /** Days of trial when this plan is given to a new company as the default; 0 = no trial. */
    @Column(name = "trial_days", nullable = false)
    private int trialDays;

    /** Feature add-on shown on the plan card (docs/analysis/06 §3.2.3). Not enforced by any service yet. */
    @Column(name = "ai_integration", nullable = false)
    private boolean aiIntegration;

    /** "Recommended" badge on the plan card (docs/analysis/06 §10.2.1); display only. */
    @Column(name = "recommended", nullable = false)
    private boolean recommended;

    /** "Free" plan (user manual §4.1): its prices must be 0 (SAAS_041). */
    @Column(name = "is_free", nullable = false)
    private boolean free;

    /**
     * Free badge text on the plan card, e.g. "Most Popular" (user manual §4.1). Independent of recommended: the FE shows
     * badge when set, else "Recommended" when recommended is true.
     */
    @Column(name = "badge", length = 50)
    private String badge;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPlan;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /**
     * Modules included in the plan (ERP plan feature list). Empty = every module, which is what plans meant before
     * they had a module list. Enforced by PlanModuleFilter when IAM resolves permissions.
     */
    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "plan_module",
            joinColumns = @jakarta.persistence.JoinColumn(name = "plan_id"))
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "module", nullable = false, length = 40)
    private java.util.Set<com.terp.erp.iam.saas.EPlanModule> modules = new java.util.HashSet<>();
}
