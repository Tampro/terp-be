package com.terp.erp.order.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Return errors (order-service module ret). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EReturnError implements ErrorCode {
    RET_001("Only a delivered order can be returned."),
    RET_002("The return period of {0} days has ended."),
    RET_003("Item {0} cannot be returned in that quantity (at most {1})."),
    RET_004("Nothing would be refunded after the pick-up fee of {0}."),
    RET_005("This order already has a return in progress: {0}."),
    RET_006("This is not allowed while the return is {0}."),
    RET_007("The return does not exist."),
    RET_008("Choose the company to act on.");

    private final String message;
}
