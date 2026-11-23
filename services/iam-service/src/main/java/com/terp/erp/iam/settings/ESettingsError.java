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
    SETTINGS_011("The Stripe keys are not {0} mode keys."),
    SETTINGS_012("{0} cannot be enabled without {1}."),
    SETTINGS_013("This URL is not allowed: {0}."),
    SETTINGS_014("Unknown webhook event: {0}."),
    SETTINGS_015("The webhook does not exist."),
    SETTINGS_016("The Google service account JSON is not valid."),
    SETTINGS_017("The {0} integration is not configured."),
    SETTINGS_018("Unknown integration: {0}."),
    SETTINGS_019("The value of {0} is not valid."),
    SETTINGS_020("Unknown notification channel: {0}."),
    SETTINGS_021("Unknown notification event: {0}."),
    SETTINGS_022("The {0} integration must be configured first."),
    SETTINGS_023("Nothing to preview: the template is not customised and the request has no text."),
    SETTINGS_024("The text is too long for {0}: at most {1} characters."),
    SETTINGS_025("Unknown placeholder: {0}."),
    SETTINGS_026("The platform webhook does not exist.");

    private final String message;
}
