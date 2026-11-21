package com.terp.erp.document.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;
import com.terp.erp.document.common.EStatus;

/** Standard employment terms (docs/analysis/06 §9.4.6). */
@Getter
@Setter
@Entity
@Table(name = "contract_type")
public class ContractType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** null = open-ended (no end date by default). */
    @Column(name = "default_duration_months")
    private Integer defaultDurationMonths;

    @Column(name = "probation_months", nullable = false)
    private int probationMonths;

    @Column(name = "notice_days", nullable = false)
    private int noticeDays;

    /** The Renewable filter of the list. */
    @Column(name = "renewable", nullable = false)
    private boolean renewable;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
