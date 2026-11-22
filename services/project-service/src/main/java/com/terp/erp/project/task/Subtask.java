package com.terp.erp.project.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;

@Getter
@Setter
@Entity
@Table(name = "prj_subtask")
public class Subtask extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "name", nullable = false, length = 191)
    private String name;

    @Column(name = "done", nullable = false)
    private boolean done;
}
