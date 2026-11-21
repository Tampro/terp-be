package com.terp.erp.iam.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** One section (SYSTEM, BRAND, CURRENCY) of a company's own settings: the fields it overrides, as JSON. */
@Getter
@Setter
@Entity
@Table(name = "company_setting")
public class CompanySetting extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "section", nullable = false, length = 20)
    private String section;

    @Column(name = "value_json", nullable = false, columnDefinition = "TEXT")
    private String valueJson;
}
