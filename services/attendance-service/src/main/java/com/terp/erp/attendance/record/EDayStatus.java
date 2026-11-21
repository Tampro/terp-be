package com.terp.erp.attendance.record;

/**
 * A cell of the attendance calendar (legend of docs/analysis/06 §6.4.1). Late, Early Departure and Overtime are flags
 * on a PRESENT or HALF_DAY cell, not statuses of their own.
 */
public enum EDayStatus {
    PRESENT, ABSENT, HALF_DAY, ON_LEAVE, HOLIDAY, DAY_OFF, FUTURE, NOT_ADDED
}
