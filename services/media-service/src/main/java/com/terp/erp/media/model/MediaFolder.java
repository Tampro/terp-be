package com.terp.erp.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A flat folder of the media library (docs/analysis/06 §3.2.2 "Folders"). {@code companyId} null = a folder of the
 * platform (super admin's own files); otherwise it belongs to that company.
 */
@Getter
@Setter
@Entity
@Table(name = "media_folder")
public class MediaFolder extends BaseAuditableEntity {
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
