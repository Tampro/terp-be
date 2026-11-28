package com.terp.erp.order.mileage;

/** EARN (+, delivered order), USE (−, placed order), RELEASE (+, cancelled / expired order), EXPIRE (−), ADJUST (±). */
public enum EMileageEntryType {
    EARN, USE, RELEASE, EXPIRE, ADJUST
}
