package com.terp.erp.member.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** member-service errors. Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EMemberError implements ErrorCode {
    MEM_001("The shop does not exist."),
    MEM_002("You are already a member of this shop."),
    MEM_003("You left this shop recently. You can join again after {0}."),
    MEM_004("You are not a member of this shop."),
    MEM_005("You must accept the terms of use and the privacy policy."),
    MEM_006("The shop is temporarily unavailable. Please try again later.");

    private final String message;
}
