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

import java.math.BigDecimal;

/** A performance indicator rated in reviews (docs/analysis/06 §7.3.4). */
@Getter
@Setter
@Entity
@Table(name = "performance_indicator")
public class PerformanceIndicator extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** As typed ("Percentage", "Rating"). */
    @Column(name = "measurement_unit", length = 50)
    private String measurementUnit;

    /** As typed ("95%", "4/5"). */
    @Column(name = "target_value", length = 100)
    private String targetValue;

    /** Weight in the review's weighted rating (PO, 2026-11-20). */
    @Column(name = "weight", nullable = false, precision = 6, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EOrgStatus status = EOrgStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
