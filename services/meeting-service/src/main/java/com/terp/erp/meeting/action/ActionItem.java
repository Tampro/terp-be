package com.terp.erp.meeting.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.meeting.common.BaseAuditableEntity;

import java.time.LocalDate;

/** A task that came out of a meeting (docs/analysis/06 §9.1.2). */
@Getter
@Setter
@Entity
@Table(name = "action_item")
public class ActionItem extends BaseAuditableEntity {
    public enum EPriority { LOW, MEDIUM, HIGH, CRITICAL }

    /** Stored stage, from the progress; OVERDUE is not stored (see ActionItemService). */
    public enum EStatus {
        NOT_STARTED, IN_PROGRESS, COMPLETED;

        public static EStatus of(int progress) {
            return progress >= 100 ? COMPLETED : progress > 0 ? IN_PROGRESS : NOT_STARTED;
        }
    }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Column(name = "assignee_name", length = 200)
    private String assigneeName;

    @Column(name = "assignee_email", length = 255)
    private String assigneeEmail;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private EPriority priority = EPriority.MEDIUM;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "progress_notes", length = 2000)
    private String progressNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.NOT_STARTED;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
