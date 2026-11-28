package com.terp.erp.order.mileage;

/**
 * EARN (+, delivered order), USE (−, placed order), RELEASE (+, cancelled / expired order), EXPIRE (−), ADJUST (±),
 * RETURN (+, points spent on returned goods), CLAWBACK (−, points earned on returned goods).
 */
public enum EMileageEntryType {
    EARN, USE, RELEASE, EXPIRE, ADJUST, RETURN, CLAWBACK
}
