package com.terp.erp.crm.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;

/** A column of the lead or deal board of one pipeline; position orders the columns (drag to reorder). */
@Getter
@Setter
@Entity
@Table(name = "crm_stage")
public class Stage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private EStageKind kind;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
