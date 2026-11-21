package com.terp.erp.employee.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;
import com.terp.erp.employee.org.EOrgStatus;

/** A review cycle of the company (docs/analysis/06 §7.3.3). */
@Getter
@Setter
@Entity
@Table(name = "review_cycle")
public class ReviewCycle extends BaseAuditableEntity {
    /** TODO(assumption): the spec names a Frequency dropdown without its values — confirm the list with BA. */
    public enum EFrequency { MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 12)
    private EFrequency frequency;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EOrgStatus status = EOrgStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
