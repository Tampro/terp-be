package com.terp.erp.employee.lifecycle;

/**
 * Transfer statuses of the ERP manual §15 / FR-ERP-CAR-001. PENDING and IN_PROGRESS are open (editable, decidable);
 * APPROVED (the employee has moved), REJECTED and CANCELLED are final.
 */
public enum ETransferStatus { PENDING, APPROVED, IN_PROGRESS, REJECTED, CANCELLED }
