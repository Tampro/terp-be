package com.terp.erp.iam.helpdesk;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Platform helpdesk errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EHelpdeskError implements ErrorCode {
    HDK_001("The ticket does not exist."),
    HDK_002("The category does not exist."),
    HDK_003("The category is not active."),
    HDK_004("The ticket is closed: it cannot receive replies."),
    HDK_005("Only the platform can post internal replies."),
    HDK_006("The reply does not exist."),
    HDK_007("An attachment must be a file of the ticket's company (image, PDF, text or Office document, at most 10 MB)."),
    HDK_008("Choose the company the ticket is for."),
    HDK_009("Only the platform sets the priority of a ticket.");

    private final String message;
}
