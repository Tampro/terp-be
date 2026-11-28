package com.terp.erp.order.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Mileage errors (order-service module mileage). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EMileageError implements ErrorCode {
    MIL_001("The mileage policy is not valid: {0}."),
    MIL_002("Choose the company to act on."),
    MIL_003("Points cannot be used in this shop now."),
    MIL_004("Use at least {0} points, in steps of 10."),
    MIL_005("Not enough points (balance {0})."),
    MIL_006("Points cannot exceed the goods amount ({0})."),
    MIL_007("This customer has no mileage in your company.");

    private final String message;
}
