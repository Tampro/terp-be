package com.terp.erp.iam.email;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EEmailError implements ErrorCode {
    EMAIL_001("The email template does not exist."),
    EMAIL_002("Unsupported language: {0}."),
    EMAIL_003("This notification cannot be turned off.");

    private final String message;
}
