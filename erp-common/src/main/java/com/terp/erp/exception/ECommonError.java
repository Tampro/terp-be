package com.terp.erp.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ECommonError implements ErrorCode {
    COMMON_001("Validation failed."),
    COMMON_002("Unexpected server error."),
    COMMON_003("Authentication required or the token is invalid."),
    COMMON_004("You do not have permission to perform this action."),
    COMMON_005("The requested resource does not exist."),
    COMMON_006("Too many requests. Please try again later.");

    private final String message;
}
