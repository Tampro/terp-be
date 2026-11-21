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
    ACC_003("This user account is already linked to another customer.");

    private final String message;
}
