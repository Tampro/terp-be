package com.terp.erp.asset.item;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.asset.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** An asset of the company (docs/analysis/06 §8.2.2). */
@Getter
@Setter
@Entity
@Table(name = "asset")
public class Asset extends BaseAuditableEntity {
    /** ASSIGNED and MAINTENANCE are set by assignments and maintenance only (PO, 2026-11-20). */
    public enum EStatus { AVAILABLE, ASSIGNED, MAINTENANCE, DISPOSED }

    public enum ECondition { NEW, GOOD, FAIR, POOR, DAMAGED }

    public enum EDepreciationMethod { STRAIGHT_LINE, DECLINING_BALANCE }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "asset_type_id", nullable = false)
    private Long assetTypeId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "asset_code", nullable = false, length = 50)
    private String assetCode;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchaseCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_condition", nullable = false, length = 10)
    private ECondition condition = ECondition.GOOD;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Column(name = "warranty_info", length = 1000)
    private String warrantyInfo;

    @Column(name = "warranty_expiry_date")
    private LocalDate warrantyExpiryDate;

    @Column(name = "image_file_id")
    private Long imageFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 20)
    private EDepreciationMethod depreciationMethod = EDepreciationMethod.STRAIGHT_LINE;

    @Column(name = "useful_life_years", nullable = false)
    private int usefulLifeYears;

    @Column(name = "salvage_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal salvageValue = BigDecimal.ZERO;

    @ElementCollection
    @CollectionTable(name = "asset_document", joinColumns = @JoinColumn(name = "asset_id"))
    @Column(name = "file_id", nullable = false)
    private Set<Long> documentFileIds = new LinkedHashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
