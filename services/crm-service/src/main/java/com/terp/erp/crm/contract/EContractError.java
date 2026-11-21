package com.terp.erp.crm.contract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Contract errors (ERP manual 7.5). Never renumber or reuse a code. */
@Getter
@RequiredArgsConstructor
public enum EContractError implements ErrorCode {
    CTR_001("Choose the company to act on."),
    CTR_002("The contract does not exist."),
    CTR_003("The contract type does not exist."),
    CTR_004("The contract type {0} already exists."),
    CTR_005("The contract type is used by contracts and cannot be deleted."),
    CTR_006("The end date must be on or after the start date."),
    CTR_007("The contract type is inactive."),
    CTR_008("The renewal does not exist."),
    CTR_009("The comment does not exist."),
    CTR_010("The note does not exist."),
    CTR_011("The attachment does not exist."),
    CTR_012("An attachment must be a file in the media library of the contract company.");

    private final String message;
}
