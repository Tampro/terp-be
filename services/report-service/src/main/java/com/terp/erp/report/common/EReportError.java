package com.terp.erp.report.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EReportError implements ErrorCode {
    REP_001("The date range can span at most {0} days."),
    REP_002("The end date must not be before the start date.");

    private final String message;
}
