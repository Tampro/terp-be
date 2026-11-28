package com.terp.erp.order.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Coupon errors (order-service module coupon). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ECouponError implements ErrorCode {
    CPN_001("A coupon with this code already exists."),
    CPN_002("The coupon is not valid: {0}."),
    CPN_003("The code of a coupon already used cannot change."),
    CPN_004("A coupon already used cannot be deleted. Switch it off instead."),
    CPN_005("The coupon does not exist."),
    CPN_006("Choose the company to act on."),
    CPN_007("This coupon code is not valid."),
    CPN_008("This coupon cannot be used now."),
    CPN_009("This coupon needs goods worth at least {0}."),
    CPN_010("This coupon has been used up."),
    CPN_011("You have already used this coupon.");

    private final String message;
}
