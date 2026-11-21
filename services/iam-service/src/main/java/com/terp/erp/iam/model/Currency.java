package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A currency of the platform (docs/analysis/06 §3.2.5, WorkDo `currencies`). Exactly one is the default.
 * Platform-wide, not per company. Plan prices are not yet tied to a currency (they are in the default one).
 */
@Getter
@Setter
@Entity
@Table(name = "currency")
public class Currency extends BaseAuditableEntity {
    /** ISO 4217, upper case. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "symbol", length = 10)
    private String symbol;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultCurrency;
}
