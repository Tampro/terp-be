package com.terp.erp.accounting.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EAccountingError implements ErrorCode {
    ACC_001("Choose the company to act on."),
    ACC_002("The customer does not exist."),
    ACC_003("This user account is already linked to another customer."),
    ACC_004("The vendor does not exist."),
    ACC_005("This user account is already linked to another vendor."),
    ACC_006("The bank account does not exist."),
    ACC_007("This account number is already used in the company."),
    ACC_008("The bank account is inactive.");

    private final String message;
}
