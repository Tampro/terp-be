package com.terp.erp.store.shop;

import com.terp.erp.store.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The public shop of a company: its address (slug) and whether customers can reach it. */
@Getter
@Setter
@Entity
@Table(name = "shop_setting")
public class ShopSetting extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "slug", nullable = false, length = 40)
    private String slug;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
