package com.terp.erp.asset.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EAssetError implements ErrorCode {
    AST_001("Choose the company to act on."),
    AST_002("The asset type does not exist."),
    AST_003("This name already exists."),
    AST_004("It is still in use and cannot be deleted."),
    AST_005("The asset does not exist."),
    AST_006("This asset code already exists."),
    AST_007("The employee service is unavailable. Please try again."),
    AST_008("The media service is unavailable. Please try again."),
    AST_009("The file does not exist in the media library of the company."),
    AST_010("The salvage value cannot be greater than the purchase cost."),
    AST_011("The file is not a valid Excel workbook (.xlsx)."),
    AST_012("The file has more than {0} rows."),
    AST_013("Row {0}: {1} is required or invalid."),
    AST_014("The uploaded file is larger than {0}."),
    AST_015("Only Available or Disposed can be chosen; Assigned and Maintenance follow assignments and maintenance."),
    AST_016("Return the asset or finish its maintenance before changing its status."),
    AST_017("The warranty expiry date cannot be before the purchase date."),
    AST_018("Only an available asset can be assigned."),
    AST_019("The expected return date cannot be before the checkout date."),
    AST_020("The asset is not assigned to anyone."),
    AST_021("The check-in date cannot be before the checkout date."),
    AST_022("A disposed asset cannot be maintained."),
    AST_023("Return the asset before starting its maintenance."),
    AST_024("The end date cannot be before the start date."),
    AST_025("The maintenance record does not exist."),
    AST_026("A completed or cancelled maintenance cannot be changed.");

    private final String message;
}
