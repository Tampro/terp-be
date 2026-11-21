package com.terp.erp.iam.saas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.time.Instant;

/** A trial a company started itself; kept so the same plan cannot be tried twice. */
@Getter
@Setter
@Entity
@Table(name = "company_plan_trial")
public class CompanyPlanTrial extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
}
