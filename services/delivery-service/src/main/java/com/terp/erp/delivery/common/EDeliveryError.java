package com.terp.erp.delivery.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** delivery-service errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EDeliveryError implements ErrorCode {
    DLV_001("The delivery does not exist."),
    DLV_002("Choose the company to act on."),
    DLV_003("The order service is temporarily unavailable. Please try again later."),
    DLV_004("This user cannot deliver for your company."),
    DLV_005("This is not allowed while the delivery is {0}."),
    DLV_006("The delivery was changed by someone else. Reload and try again."),
    DLV_007("The delivery is already {0}; the order can no longer be cancelled.");

    private final String message;
}
