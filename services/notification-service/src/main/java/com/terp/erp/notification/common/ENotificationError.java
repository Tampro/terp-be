package com.terp.erp.notification.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Notification-service errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ENotificationError implements ErrorCode {
    NTF_001("Choose the company to act on."),
    NTF_002("The delivery does not exist."),
    NTF_003("Only a failed delivery can be retried."),
    NTF_004("The IAM service is unavailable. Please try again later.");

    private final String message;
}
