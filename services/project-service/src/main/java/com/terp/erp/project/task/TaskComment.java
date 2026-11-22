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
@Table(name = "prj_task_comment")
public class TaskComment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
