package com.terp.erp.accounting.budget;

/** ACTIVE → close (only after the end date) → CLOSED; a closed period and its budgets are read-only. */
public enum EBudgetPeriodStatus {
    ACTIVE, CLOSED
}
