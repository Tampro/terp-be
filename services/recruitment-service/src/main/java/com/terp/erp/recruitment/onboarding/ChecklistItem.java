package com.terp.erp.recruitment.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;
import com.terp.erp.recruitment.common.EStatus;

/** A task of an onboarding checklist (docs/analysis/06 §7.1.8). */
@Getter
@Setter
@Entity
@Table(name = "checklist_item")
public class ChecklistItem extends BaseAuditableEntity {
    /** Fixed list (PO, 2026-11-20). */
    public enum ECategory { DOCUMENTATION, IT_SETUP, TRAINING, HR, ORIENTATION, OTHER }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "checklist_id", nullable = false)
    private Long checklistId;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ECategory category;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    /** Days after the onboarding start date; null = no due day. */
    @Column(name = "due_day")
    private Integer dueDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
