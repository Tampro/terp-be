package com.terp.erp.crm.setup;

/** Namespace of the CRM setup entities (pipelines, stages, labels, sources); see the nested classes. */
public final class SetupEntities {
    private SetupEntities() {
    }

    /** Lead stages and deal stages are separate lists of the same pipeline (ERP manual 7.1). */
    public enum EStageKind { LEAD, DEAL }
}
