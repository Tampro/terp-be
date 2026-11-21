package com.terp.erp.crm.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

/** A coloured tag of one pipeline, put on its leads and deals. */
@Getter
@Setter
@Entity
@Table(name = "crm_label")
public class Label extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** #RRGGBB. */
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
