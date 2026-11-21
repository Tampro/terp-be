package com.terp.erp.iam.helpdesk;

/** Platform helpdesk ticket status (ERP manual 3.3). Only the super admin changes it; replies do not. */
public enum EHelpdeskStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED
}
