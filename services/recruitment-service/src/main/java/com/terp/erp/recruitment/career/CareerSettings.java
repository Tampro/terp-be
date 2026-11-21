package com.terp.erp.recruitment.career;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

/** A company's public career page (docs/analysis/06 §7.1.9): /api/v1/public/career/{slug}, shown only when enabled. */
@Getter
@Setter
@Entity
@Table(name = "career_settings")
public class CareerSettings extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "headline", length = 200)
    private String headline;

    @Column(name = "tagline", length = 500)
    private String tagline;
}
