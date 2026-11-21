package com.terp.erp.iam.helpdesk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/**
 * Category of platform helpdesk tickets. Belongs to the platform (no company): the super admin manages them and
 * every company picks from the active ones.
 */
@Getter
@Setter
@Entity
@Table(name = "helpdesk_category")
public class HelpdeskCategory extends BaseAuditableEntity {
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** #RRGGBB. */
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
