package com.terp.erp.iam.saas;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ESaasError implements ErrorCode {
    SAAS_001("The plan does not exist."),
    SAAS_002("The plan code is already taken."),
    SAAS_003("This plan has no yearly price."),
    SAAS_004("The coupon does not exist."),
    SAAS_005("The coupon code is already taken."),
    SAAS_006("The coupon is invalid, expired or used up."),
    SAAS_007("The order total is below the coupon minimum spend."),
    SAAS_008("The order does not exist."),
    SAAS_009("Only a pending order can be approved, rejected or cancelled."),
    SAAS_010("The company already has a pending order."),
    SAAS_011("The plan user limit ({0}) is reached. Upgrade the plan to add accounts."),
    SAAS_012("A percentage discount must be between 0 and 100."),
    SAAS_013("The default plan cannot be deleted."),
    SAAS_014("The plan is not available."),
    SAAS_015("The default plan cannot be made inactive."),
    SAAS_016("The plan request does not exist."),
    SAAS_017("Only a pending plan request can be approved, rejected or cancelled."),
    SAAS_018("The company already has a pending plan request."),
    SAAS_019("The order total is above the coupon maximum spend."),
    SAAS_020("The minimum spend cannot be greater than the maximum spend."),
    SAAS_021("A coupon code is required for manual entry."),
    SAAS_022("The currency does not exist."),
    SAAS_023("The currency code is already taken."),
    SAAS_024("The default currency cannot be deleted."),
    SAAS_025("Set another currency as default instead of removing the default flag."),
    SAAS_026("The referral code does not exist."),
    SAAS_027("The payout amount is below the minimum threshold of {0}."),
    SAAS_028("The payout amount is above the available balance of {0}."),
    SAAS_029("There is already a pending payout request."),
    SAAS_030("The payout request does not exist."),
    SAAS_031("Only a pending payout request can be approved or rejected."),
    SAAS_032("The referral program is disabled."),
    SAAS_033("The receipt must be a PDF, JPG or PNG file of your company, at most 5 MB."),
    SAAS_034("This plan has no trial."),
    SAAS_035("Your company has already tried this plan."),
    SAAS_036("Your company has a paid plan running until {0}; a trial would replace it."),
    SAAS_037("Stripe payment is not available."),
    SAAS_038("Stripe could not start the payment: {0}."),
    SAAS_039("This order cannot be paid this way."),
    SAAS_040("The Stripe notification is not valid."),
    SAAS_041("A free plan must have a price of 0.");

    private final String message;
}
