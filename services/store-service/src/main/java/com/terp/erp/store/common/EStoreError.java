package com.terp.erp.store.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Store-service errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EStoreError implements ErrorCode {
    STO_001("The store does not exist."),
    STO_002("This store code is already used."),
    STO_003("This warehouse already belongs to store {0}."),
    STO_004("The chosen warehouse does not exist in the company or is inactive."),
    STO_005("The order hours are invalid: the start must be before the end, in a valid time zone."),
    STO_006("The inventory service is unavailable. Please try again later."),
    STO_007("Choose the company to act on.");

    private final String message;
}
