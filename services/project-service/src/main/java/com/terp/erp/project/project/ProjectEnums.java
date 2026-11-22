package com.terp.erp.project.project;

/** Enums of the project module (ERP manual 11.1). */
public final class ProjectEnums {
    private ProjectEnums() {
    }

    /** FINISHED is labelled "Completed" on the card (C-05); overdue is computed, never stored. */
    public enum EProjectStatus { ONGOING, ON_HOLD, FINISHED }

    public enum EMilestoneStatus { INCOMPLETE, ONGOING, COMPLETE }

    /** Spec: the reference TERP Core (Taskly project_tasks / project_bugs) has High, Medium, Low only. */
    public enum EPriority { LOW, MEDIUM, HIGH }

    /** Task boards and bug boards have their own stage lists. */
    public enum EStageKind { TASK, BUG }
}
