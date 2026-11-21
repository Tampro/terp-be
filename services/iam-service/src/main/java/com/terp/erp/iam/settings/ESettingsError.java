package com.terp.erp.iam.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ESettingsError implements ErrorCode {
    SETTINGS_001("Unknown time zone: {0}."),
    SETTINGS_002("The encryption key for secrets is not configured (ERP_SETTINGS_ENC_KEY)."),
    SETTINGS_003("The email settings are not complete."),
    SETTINGS_004("The test email could not be sent: {0}."),
    SETTINGS_005("These file types cannot be allowed: {0}."),
    SETTINGS_006("A secret is required to enable reCAPTCHA."),
    SETTINGS_007("A stored secret cannot be read with the current encryption key."),
    SETTINGS_008("The language {0} does not exist or is disabled."),
    SETTINGS_009("The currency {0} does not exist."),
    SETTINGS_010("Stripe needs a publishable key, a secret key and a webhook signing secret to be enabled."),
    SETTINGS_011("The Stripe keys are not {0} mode keys.");

    private final String message;
}
