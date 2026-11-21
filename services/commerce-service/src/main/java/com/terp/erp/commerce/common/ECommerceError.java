package com.terp.erp.commerce.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Sales errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ECommerceError implements ErrorCode {
    SAL_001("The document does not exist."),
    SAL_002("Only a draft can be changed, sent, posted or deleted."),
    SAL_003("Only a sent document can be accepted or rejected."),
    SAL_004("Only an accepted document can be converted to an invoice."),
    SAL_005("This document was already converted to invoice {0}."),
    SAL_006("Choose the company to act on."),
    SAL_007("The due date cannot be before the document date."),
    SAL_008("Item {0} cannot be used here: a service-wise invoice takes services only, and stock items need a product-wise invoice."),
    SAL_009("A product-wise invoice needs a warehouse."),
    SAL_010("Only a posted invoice can be returned."),
    SAL_011("Return quantity for {0} is more than the {1} still returnable."),
    SAL_012("The return does not exist."),
    SAL_013("Only a draft return can be changed, approved or deleted."),
    SAL_014("Only an approved return can be completed."),
    SAL_015("The inventory service is unavailable. Please try again later."),
    SAL_016("The accounting service is unavailable. Please try again later."),
    SAL_017("A new version can be made only of a sent, accepted or rejected quotation."),
    SAL_018("Line {0} is not a line of the original invoice."),
    SAL_019("A service-wise invoice has no warehouse and no quantity other than 1.");

    private final String message;
}
