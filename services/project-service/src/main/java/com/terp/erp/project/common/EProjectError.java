package com.terp.erp.project.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Project-service errors (ERP manual 11.1, 11.2). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EProjectError implements ErrorCode {
    PRJ_001("Choose the company to act on."),
    PRJ_002("The media service is unavailable. Please try again later."),
    PRJ_003("The CRM service is unavailable. Please try again later."),
    PRJ_004("The accounting service is unavailable. Please try again later."),
    PRJ_005("The IAM service is unavailable. Please try again later."),
    PRJ_006("The project does not exist."),
    PRJ_007("The end date must not be before the start date."),
    PRJ_008("User {0} is not a user of this company."),
    PRJ_009("Customer {0} is not a customer of this company."),
    PRJ_010("The milestone does not exist."),
    PRJ_011("The milestone does not belong to this project."),
    PRJ_012("The stage does not exist."),
    PRJ_013("The new order must list every stage of that kind exactly once."),
    PRJ_014("Stage \"{0}\" still holds tasks or bugs and cannot be deleted."),
    PRJ_015("The last remaining stage cannot be deleted."),
    PRJ_016("The task does not exist."),
    PRJ_017("User {0} is not a member of the project team."),
    PRJ_018("The subtask does not exist."),
    PRJ_019("The comment does not exist."),
    PRJ_020("The bug does not exist."),
    PRJ_021("The file cannot be attached: it is not a file of this company."),
    PRJ_022("The attachment does not exist."),
    PRJ_023("The date range is invalid: from must not be after to, at most 366 days."),
    PRJ_024("The stage is not a stage of this board."),
    PRJ_025("The due date must not be before the start date.");

    private final String message;
}
