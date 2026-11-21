package com.terp.erp.iam.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies of /api/v1/auth. */
public final class AuthForms {
    private AuthForms() {
    }

    public record LoginForm(
            @Schema(description = "Username", example = "hr.demo", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String username,
            @Schema(description = "Password", example = "********", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 128) String password,
            @Schema(description = "reCAPTCHA token; required when reCAPTCHA is enabled in the settings")
            @Size(max = 4000) String recaptchaToken) {
    }

    public record RefreshTokenForm(
            @Schema(description = "Refresh token from login or the previous refresh",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String refreshToken) {
    }

    public record InitialPasswordForm(
            @Schema(description = "Username", example = "hr.demo", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String username,
            @Schema(description = "Temporary password given by the administrator",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 128) String currentPassword,
            @Schema(description = "New password; the realm password policy applies",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 8, max = 128) String newPassword,
            @Schema(description = "reCAPTCHA token; required when reCAPTCHA is enabled in the settings")
            @Size(max = 4000) String recaptchaToken) {
    }

    public record ForgotPasswordForm(
            @Schema(example = "hr.demo@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "reCAPTCHA token; required when reCAPTCHA is enabled in the settings")
            @Size(max = 4000) String recaptchaToken) {
    }

    public record ResetPasswordForm(
            @Schema(description = "Token of the emailed link", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String token,
            @Schema(description = "New password; the realm password policy applies",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record RegisterForm(
            @Schema(description = "Your full name", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,
            @Schema(description = "Also your login name and the company email", example = "owner@example.com",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 100) String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 8, max = 128) String password,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 128) String confirmPassword,
            @Schema(description = "Company name; omitted = your name") @Size(max = 255) String companyName,
            @Schema(description = "Referral code of the company that referred you", example = "REF000002")
            @Size(max = 20) String referralCode,
            @Schema(description = "reCAPTCHA token; required when reCAPTCHA is enabled in the settings")
            @Size(max = 4000) String recaptchaToken) {
        @Schema(hidden = true)
        @jakarta.validation.constraints.AssertTrue(message = "confirmPassword must equal password")
        public boolean isPasswordConfirmed() {
            return password == null || password.equals(confirmPassword);
        }
    }

    public record RegisteredDto(String companyId, String companyCode,
                                @Schema(description = "true = log in only after opening the link sent to the email")
                                boolean emailVerificationRequired) {
    }

    public record TokenForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String token) {
    }

    @Schema(description = "Tokens. The FE sends accessToken as Bearer and calls refresh-token on 401.")
    public record TokenDto(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) {
    }
}
