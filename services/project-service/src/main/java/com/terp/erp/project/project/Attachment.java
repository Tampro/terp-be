package com.terp.erp.project.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;

/** A media-service file of the project company; the name is copied for display. */
@Getter
@Setter
@Entity
@Table(name = "prj_attachment")
public class Attachment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "name", length = 255)
    private String name;
}
