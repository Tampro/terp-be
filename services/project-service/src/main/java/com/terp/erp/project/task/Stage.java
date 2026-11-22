package com.terp.erp.project.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;
import com.terp.erp.project.project.ProjectEnums.EStageKind;

/** A column of the task or bug board of a company; position orders the columns, the last TASK stage is "done". */
@Getter
@Setter
@Entity
@Table(name = "prj_stage")
public class Stage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

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
