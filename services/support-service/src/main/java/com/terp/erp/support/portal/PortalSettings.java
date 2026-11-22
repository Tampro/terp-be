package com.terp.erp.support.portal;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Support portal settings of one company (reference support_ticket_settings key/value rows + quick links). The slug
 * (unique across companies) is the portal address; the portal answers only while enabled. content holds the JSON of
 * PortalSettingsService.PortalContent.
 */
@Getter
@Setter
@Entity
@Table(name = "portal_settings")
public class PortalSettings extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "brand_name", nullable = false, length = 150)
    private String brandName;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;
}
