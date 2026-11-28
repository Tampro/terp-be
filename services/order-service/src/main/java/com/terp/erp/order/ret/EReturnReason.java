package com.terp.erp.order.ret;

/** FR-MYO-008: a change of mind pays the pick-up fee; a defect or a wrong item is free. */
public enum EReturnReason {
    CHANGE_OF_MIND, DEFECT, WRONG_ITEM;

    public boolean chargesFee() {
        return this == CHANGE_OF_MIND;
    }
}
