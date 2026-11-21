package com.terp.erp.commerce.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** POS errors (ERP manual 10). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EPosError implements ErrorCode {
    POS_001("The POS order does not exist."),
    POS_002("Choose the company to act on."),
    POS_003("Select a bank account before payment."),
    POS_004("The bank account is inactive."),
    POS_005("The discount cannot be more than the subtotal plus taxes."),
    POS_006("The warehouse is inactive."),
    POS_007("The report period must be at most 366 days, and from must not be after to."),
    POS_008("At most {0} barcode labels in one PDF.");

    private final String message;
}
