package com.terp.erp.commerce.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Purchase errors (ERP manual 8.1, 8.2). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EPurchaseError implements ErrorCode {
    PUR_001("The purchase invoice does not exist."),
    PUR_002("Only a draft purchase invoice can be changed, posted or deleted."),
    PUR_003("Choose the company to act on."),
    PUR_004("The due date cannot be before the invoice date."),
    PUR_005("A purchase invoice with stock items needs a receiving warehouse."),
    PUR_006("Only a posted purchase invoice can be returned."),
    PUR_007("Return quantity for {0} is more than the {1} still returnable."),
    PUR_008("The purchase return does not exist."),
    PUR_009("Only a draft purchase return can be changed, approved or deleted."),
    PUR_010("Only an approved purchase return can be completed."),
    PUR_011("Line {0} is not a line of the original invoice."),
    PUR_012("No items available for return."),
    PUR_013("The period must end on or after its start and last at most {0} days."),
    PUR_014("Only a posted or partly paid purchase invoice can take a payment."),
    PUR_015("The payment amount {0} is more than the purchase invoice balance {1}."),
    PUR_016("Payment {0} was already applied to this purchase invoice with a different amount."),
    PUR_017("Only a purchase document whose accounting journal failed can have its journal posted again."),
    PUR_018("Your account is not linked to a vendor.");

    private final String message;
}
