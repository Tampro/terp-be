package com.terp.erp.recruitment.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;
import com.terp.erp.recruitment.common.EStatus;

/** Reusable onboarding checklist (docs/analysis/06 §7.1.7); at most one default per company. */
@Getter
@Setter
@Entity
@Table(name = "onboarding_checklist")
public class OnboardingChecklist extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultChecklist;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
