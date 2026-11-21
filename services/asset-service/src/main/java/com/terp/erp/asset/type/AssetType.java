package com.terp.erp.asset.type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.asset.common.BaseAuditableEntity;

/** A category of assets (docs/analysis/06 §8.2.4). */
@Getter
@Setter
@Entity
@Table(name = "asset_type")
public class AssetType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
