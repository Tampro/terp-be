package com.terp.erp.iam.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.auth.AuthForms.InitialPasswordForm;
import com.terp.erp.iam.auth.AuthForms.LoginForm;
import com.terp.erp.iam.auth.AuthForms.RefreshTokenForm;
import com.terp.erp.iam.auth.AuthForms.TokenDto;
import com.terp.erp.iam.auth.AuthForms.ForgotPasswordForm;
import com.terp.erp.iam.auth.AuthForms.RegisterForm;
import com.terp.erp.iam.auth.AuthForms.RegisteredDto;
import com.terp.erp.iam.auth.AuthForms.ResetPasswordForm;
import com.terp.erp.iam.auth.AuthForms.TokenForm;
import com.terp.erp.iam.login.LoginHistoryService;

@Tag(name = "Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final com.terp.erp.iam.settings.RecaptchaVerifier recaptcha;
    private final LoginHistoryService loginHistory;
    private final AccountService accounts;
    private final EmailVerificationService verification;
    private final AuthRateLimiter limiter;

    @Operation(summary = "Log in with username and password",
            description = "Returns Keycloak tokens. Access token lives 15 minutes, the refresh token rotates.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in"),
            @ApiResponse(responseCode = "400", description = "Temporary password must be changed first (AUTH_004), "
                    + "reCAPTCHA missing or refused (AUTH_010)"),
            @ApiResponse(responseCode = "401", description = "Wrong credentials (AUTH_001), account locked or "
                    + "company inactive (AUTH_002), too many attempts (AUTH_008)"),
            @ApiResponse(responseCode = "403", description = "No ERP profile for this account (AUTH_006), IP not "
                    + "allowed by the company's IP restriction (AUTH_012)"),
            @ApiResponse(responseCode = "503", description = "Keycloak unreachable (AUTH_005)")})
    @PostMapping("/login")
    public ApiMessageDto<TokenDto> login(@Valid @RequestBody LoginForm form, HttpServletRequest request) {
        recaptcha.check(form.recaptchaToken());
        TokenDto tokens = authService.login(form.username(), form.password(), clientIp(request));
        loginHistory.record(form.username(), request);
        return ApiMessageDto.ok(tokens);
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New tokens; the old refresh token is now invalid"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired (AUTH_003), "
                    + "account locked or company inactive (AUTH_002)")})
    @PostMapping("/refresh-token")
    public ApiMessageDto<TokenDto> refresh(@Valid @RequestBody RefreshTokenForm form, HttpServletRequest request) {
        return ApiMessageDto.ok(authService.refresh(form.refreshToken(), clientIp(request)));
    }

    @Operation(summary = "End the session of a refresh token",
            description = "Always answers 200; access tokens already issued stay valid until they expire.")
    @PostMapping("/logout")
    public ApiMessageDto<Void> logout(@Valid @RequestBody RefreshTokenForm form) {
        authService.logout(form.refreshToken());
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Replace the temporary password at first login, then log in")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password set; returns tokens"),
            @ApiResponse(responseCode = "400", description = "Password policy not met (AUTH_007)"),
            @ApiResponse(responseCode = "401", description = "Wrong current password (AUTH_001), "
                    + "account locked (AUTH_002, AUTH_008)")})
    @PostMapping("/initial-password")
    public ApiMessageDto<TokenDto> initialPassword(@Valid @RequestBody InitialPasswordForm form,
                                                   HttpServletRequest request) {
        recaptcha.check(form.recaptchaToken());
        TokenDto tokens = authService.setInitialPassword(form.username(), form.currentPassword(),
                form.newPassword(), clientIp(request));
        loginHistory.record(form.username(), request);
        return ApiMessageDto.ok(tokens);
    }

    @Operation(summary = "Forgot password: email a reset link",
            description = "Always 200, whether or not the email has an account. The link is valid 30 minutes, once; "
                    + "earlier links stop working.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "reCAPTCHA missing or refused (AUTH_010)"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/forgot-password")
    public ApiMessageDto<Void> forgotPassword(@Valid @RequestBody ForgotPasswordForm form, HttpServletRequest request) {
        limiter.checkIp("forgot", clientIp(request));
        recaptcha.check(form.recaptchaToken());
        if (limiter.allowEmail("forgot", form.email())) {
            accounts.forgotPassword(form.email());
        }
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Reset password with the emailed link",
            description = "Sets the new password and ends every session of the account; then log in.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Link invalid, used or expired (AUTH_014), password "
                    + "policy not met (AUTH_007)"),
            @ApiResponse(responseCode = "503", description = "Keycloak unreachable (AUTH_005)")})
    @PostMapping("/reset-password")
    public ApiMessageDto<Void> resetPassword(@Valid @RequestBody ResetPasswordForm form) {
        accounts.resetPassword(form.token(), form.newPassword());
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Sign up: create a company and become its administrator",
            description = "Only when System Settings User Registration is on. With Email Verification on, log in "
                    + "only after opening the link sent to the email (until then login answers AUTH_016).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation (passwords differ), email already used "
                    + "(IAM_010), user refused by Keycloak e.g. password policy (IAM_014), reCAPTCHA (AUTH_010)"),
            @ApiResponse(responseCode = "403", description = "Self-registration not enabled (AUTH_015)"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/register")
    public ApiMessageDto<RegisteredDto> register(@Valid @RequestBody RegisterForm form, HttpServletRequest request) {
        limiter.checkIp("register", clientIp(request));
        recaptcha.check(form.recaptchaToken());
        var c = accounts.register(form.name(), form.email(), form.password(), form.companyName(), form.referralCode());
        return ApiMessageDto.ok(new RegisteredDto(c.getId().toString(), c.getCode(), c.isEmailVerifyRequired()));
    }

    @Operation(summary = "Verify the company email with the emailed link", description = "One-time.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Link invalid, used or expired (AUTH_014)"))
    @PostMapping("/verify-email")
    public ApiMessageDto<Void> verifyEmail(@Valid @RequestBody TokenForm form) {
        verification.verify(form.token());
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Send the verification link again",
            description = "Always 200; a company already verified (or an unknown email) gets nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "reCAPTCHA missing or refused (AUTH_010)"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (AUTH_013)")})
    @PostMapping("/resend-verification")
    public ApiMessageDto<Void> resendVerification(@Valid @RequestBody ForgotPasswordForm form,
                                                  HttpServletRequest request) {
        limiter.checkIp("verify", clientIp(request));
        recaptcha.check(form.recaptchaToken());
        if (limiter.allowEmail("verify", form.email())) {
            verification.resend(form.email());
        }
        return ApiMessageDto.ok(null);
    }

    private static String clientIp(HttpServletRequest request) {
        return LoginHistoryService.clientIp(request.getHeader("X-Client-IP"), request.getRemoteAddr());
    }
}
