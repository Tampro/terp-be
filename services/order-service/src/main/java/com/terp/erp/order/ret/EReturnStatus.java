package com.terp.erp.order.ret;

/** REQUESTED -> APPROVED / REJECTED; APPROVED -> COMPLETED once Back Office has the goods back. */
public enum EReturnStatus {
    REQUESTED, APPROVED, REJECTED, COMPLETED
}
