package com.terp.erp.document.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EDocumentError implements ErrorCode {
    DOC_001("Choose the company to act on."),
    DOC_002("The document category does not exist."),
    DOC_003("This name already exists."),
    DOC_004("It is still in use and cannot be deleted."),
    DOC_005("The contract type does not exist."),
    DOC_006("The employee service is unavailable. Please try again."),
    DOC_007("The media service is unavailable. Please try again."),
    DOC_008("The file does not exist in the media library of the company."),
    DOC_009("The document does not exist."),
    DOC_010("The expiry date cannot be before the effective date."),
    DOC_011("The acknowledgment does not exist."),
    DOC_012("The employee already has an open acknowledgment for this document."),
    DOC_013("The employee is not an employee of the company."),
    DOC_014("Only a pending acknowledgment can be acknowledged."),
    DOC_015("{0} is inactive and cannot be chosen."),
    DOC_016("An archived document cannot be assigned."),
    DOC_017("The contract does not exist."),
    DOC_018("The end date cannot be before the start date."),
    DOC_019("The template does not exist."),
    DOC_020("An inactive template cannot be used."),
    DOC_021("Fill in: {0}."),
    DOC_022("Variable names use letters, digits and underscores only: {0}."),
    DOC_023("Default values must be a JSON object of text values."),
    DOC_024("Only a pending document can be approved or rejected."),
    DOC_025("The document can be downloaded only once it is approved or published."),
    DOC_026("Acknowledge the document before downloading it."),
    DOC_027("It is {0} and can no longer be edited or deleted."),
    DOC_028("The status cannot change from {0} to {1}.");

    private final String message;
}
