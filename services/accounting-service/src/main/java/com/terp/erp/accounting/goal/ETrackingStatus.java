package com.terp.erp.accounting.goal;

/**
 * Progress % minus elapsed-time %: at least +10 points AHEAD, within ±10 ON_TRACK, down to −25 BEHIND, lower
 * CRITICAL. TODO(assumption): thresholds are not in the manual.
 */
public enum ETrackingStatus {
    AHEAD, ON_TRACK, BEHIND, CRITICAL
}
