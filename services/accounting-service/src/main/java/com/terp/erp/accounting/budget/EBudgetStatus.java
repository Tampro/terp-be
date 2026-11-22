package com.terp.erp.accounting.budget;

/**
 * DRAFT → approve → APPROVED → activate → ACTIVE → close → CLOSED. TODO(assumption): the manual gives the three
 * actions without their order (ARQ C-06 / Q-11); this order is the one implemented until the BA confirms.
 */
public enum EBudgetStatus {
    DRAFT, APPROVED, ACTIVE, CLOSED
}
