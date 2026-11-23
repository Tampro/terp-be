package com.terp.erp.crm.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** CRM errors (pipelines, leads, deals). Contracts have EContractError. Never renumber or reuse a code. */
@Getter
@RequiredArgsConstructor
public enum ECrmError implements ErrorCode {
    CRM_001("Choose the company to act on."),
    CRM_002("The pipeline does not exist."),
    CRM_003("The stage does not exist."),
    CRM_004("The label does not exist."),
    CRM_005("The source does not exist."),
    CRM_006("The lead does not exist."),
    CRM_007("The deal does not exist."),
    CRM_008("The stage does not belong to this pipeline or is not a {0} stage."),
    CRM_009("{0} is still used by leads or deals and cannot be deleted."),
    CRM_010("A pipeline needs at least one lead stage and one deal stage before it can be used."),
    CRM_011("This lead was already converted to deal {0}."),
    CRM_012("The user {0} is not an account of your company."),
    CRM_013("The record does not exist."),
    CRM_014("An attachment must be a file of your company's media library."),
    CRM_015("The accounting service is unavailable. Please try again later."),
    CRM_016("The media service is unavailable. Please try again later."),
    CRM_017("The inventory service is unavailable. Please try again later."),
    CRM_018("Choose an existing client or give the new client's name and email."),
    CRM_019("The name {0} already exists in this pipeline."),
    CRM_020("The stage list must contain exactly the stages of the pipeline."),
    CRM_021("The period must end on or after its start and last at most {0} days."),
    CRM_022("Your account is not linked to a customer.");

    private final String message;
}
