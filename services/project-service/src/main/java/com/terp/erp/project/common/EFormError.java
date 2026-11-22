package com.terp.erp.project.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Form builder errors (ERP manual 11.2). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EFormError implements ErrorCode {
    FRM_001("The form does not exist."),
    FRM_002("This form is not available."),
    FRM_003("Field \"{0}\" of type {1} needs at least one option."),
    FRM_004("Field \"{0}\" is required."),
    FRM_005("Field \"{0}\" must be a valid email address."),
    FRM_006("Field \"{0}\" must be a number."),
    FRM_007("Field \"{0}\" must be a valid phone number."),
    FRM_008("Field \"{0}\" must be a valid http or https URL."),
    FRM_009("Field \"{0}\" must be a date (yyyy-MM-dd)."),
    FRM_010("Field \"{0}\" must be a time (HH:mm)."),
    FRM_011("Field \"{0}\" has a value that is not one of its options."),
    FRM_012("Field \"{0}\" is too long (at most {1} characters)."),
    FRM_013("The answer \"{0}\" does not match a field of this form."),
    FRM_014("The response does not exist."),
    FRM_015("This response was already converted to {0} {1}."),
    FRM_016("The mapping refers to field \"{0}\", which is not in this form."),
    FRM_017("Converting to {0} needs a value for \"{1}\"."),
    FRM_018("The value for \"{0}\" is not valid."),
    FRM_019("The submission is too large."),
    FRM_020("\"from\" must be on or before \"to\"."),
    FRM_021("A form has at most {0} fields, and a field at most {1} options."),
    FRM_022("Field keys must be unique within a form: \"{0}\" is repeated."),
    FRM_023("\"{0}\" is not a field that {1} accepts.");

    private final String message;
}
