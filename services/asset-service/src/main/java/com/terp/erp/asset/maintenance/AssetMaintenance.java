package com.terp.erp.asset.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.asset.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Maintenance of an asset (docs/analysis/06 §8.2.2). */
@Getter
@Setter
@Entity
@Table(name = "asset_maintenance")
public class AssetMaintenance extends BaseAuditableEntity {
    /** TODO(assumption): the spec names only "Preventive"; the other types are a proposal — confirm with BA. */
    public enum EType { PREVENTIVE, CORRECTIVE, REPAIR, INSPECTION, UPGRADE }

    public enum EStatus { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", nullable = false, length = 12)
    private EType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(name = "details", length = 2000)
    private String details;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status;
}
