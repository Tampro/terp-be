package com.terp.erp.project.task;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;
import com.terp.erp.project.project.ProjectEnums.EPriority;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "prj_task")
public class Task extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(name = "title", nullable = false, length = 191)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private EPriority priority = EPriority.MEDIUM;

    @Column(name = "stage_id", nullable = false)
    private Long stageId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** IAM user ids, all on the project team. */
    @ElementCollection
    @CollectionTable(name = "prj_task_assignee", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "user_id")
    private Set<Long> assigneeIds = new LinkedHashSet<>();
}
