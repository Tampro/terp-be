package com.terp.erp.iam.language;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** A language of the UI (docs/analysis/06 §3 Language Management). */
@Getter
@Setter
@Entity
@Table(name = "language")
public class Language extends BaseAuditableEntity {
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** vi/en/ko: the API and email texts exist in them; never deleted. */
    @Column(name = "system_lang", nullable = false)
    private boolean systemLang;
}
