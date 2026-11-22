package com.terp.erp.project.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;
import com.terp.erp.project.project.ProjectEnums.EPriority;

/** A bug of a project; its BUG stage is its status (FR-PRJ-005). */
@Getter
@Setter
@Entity
@Table(name = "prj_bug")
public class Bug extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "title", nullable = false, length = 191)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private EPriority priority = EPriority.MEDIUM;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "stage_id", nullable = false)
    private Long stageId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
