package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A revenue or expense category; its GL account is the default account of the revenues/expenses filed under it. */
@Getter
@Setter
@Entity
@Table(name = "account_category")
public class AccountCategory extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    private ECategoryKind kind;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
