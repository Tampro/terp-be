package com.terp.erp.support.portal;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A custom page of the portal, e.g. privacy policy or terms (reference support_ticket_custom_pages: title, slug,
 * contents, description, enable_page_footer). Slug unique within the company.
 */
@Getter
@Setter
@Entity
@Table(name = "portal_page")
public class PortalPage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "contents", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contents;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "show_in_footer", nullable = false)
    private boolean showInFooter;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
