package com.terp.erp.attendance.record;

/** Stored status of a record (doc 03). ON_LEAVE is set by the leave module (6.5) once it exists. */
public enum ERecordStatus {
    PRESENT, ABSENT, HALF_DAY, ON_LEAVE, HOLIDAY
}
