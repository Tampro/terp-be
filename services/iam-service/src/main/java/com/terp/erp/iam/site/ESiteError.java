package com.terp.erp.iam.site;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Website (docs/analysis/06 §3.2.7-3.2.10). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ESiteError implements ErrorCode {
    SITE_001("The landing page section {0} does not exist."),
    SITE_002("A section must be a JSON object of at most 64 KB."),
    SITE_003("The landing page is switched off."),
    SITE_004("The page does not exist."),
    SITE_005("The inquiry does not exist."),
    SITE_006("The subscriber does not exist."),
    SITE_007("The period must end on or after its start and last at most {0} days."),
    SITE_008("The reply could not be sent: {0}.");

    private final String message;
}
