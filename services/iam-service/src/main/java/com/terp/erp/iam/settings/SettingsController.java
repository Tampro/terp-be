package com.terp.erp.iam.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.settings.SettingsDtos.BankTransferSettings;
import com.terp.erp.iam.settings.SettingsDtos.BrandSettings;
import com.terp.erp.iam.settings.SettingsDtos.CookieSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencyFormatSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencySettingsView;
import com.terp.erp.iam.settings.SettingsDtos.EmailSettingsForm;
import com.terp.erp.iam.settings.SettingsDtos.EmailSettingsView;
import com.terp.erp.iam.settings.SettingsDtos.PublicSettings;
import com.terp.erp.iam.settings.SettingsDtos.RecaptchaSettingsForm;
import com.terp.erp.iam.settings.SettingsDtos.RecaptchaSettingsView;
import com.terp.erp.iam.settings.SettingsDtos.SeoSettings;
import com.terp.erp.iam.settings.SettingsDtos.StorageSettings;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;
import com.terp.erp.iam.settings.SettingsDtos.TestEmailForm;

/**
 * Settings page of the super admin (docs/analysis/06 §3.3.2). Each section: GET, and PUT as a full replacement.
 * Secrets (SMTP password, reCAPTCHA secret) are write-only. Payment gateways other than bank transfer, S3/Wasabi,
 * ChatGPT are deliberately not here (PO, 2026-11-19); cache is CacheAdminController.
 */
@Tag(name = "Settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings")
@SecurityRequirement(name = "bearerAuth")
public class SettingsController {
    private final SettingsService settings;

    // ------------------------------------------------------------------ open reads

    @Operation(summary = "Public settings for the login page and the app shell (no token)",
            description = "System preferences, brand, currency format and default currency, SEO, cookie banner, "
                    + "reCAPTCHA site key when enabled. Never a secret.")
    @GetMapping("/public")
    public ApiMessageDto<PublicSettings> publicSettings() {
        return ApiMessageDto.ok(settings.publicSettings());
    }

    @Operation(summary = "Upload limits, read by media-service with the caller's token")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/storage")
    public ApiMessageDto<StorageSettings> storageForServices() {
        return ApiMessageDto.ok(settings.storage());
    }

    // ------------------------------------------------------------------ super admin

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/system")
    public ApiMessageDto<SystemSettings> system() {
        return ApiMessageDto.ok(settings.system());
    }

    @Operation(summary = "Save System Settings")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unknown time zone (SETTINGS_001)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/system")
    public ApiMessageDto<SystemSettings> saveSystem(@Valid @RequestBody SystemSettings form) {
        return ApiMessageDto.ok(settings.saveSystem(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/brand")
    public ApiMessageDto<BrandSettings> brand() {
        return ApiMessageDto.ok(settings.brand());
    }

    @Operation(summary = "Save Brand Settings", description = "Logos/favicon are URLs of files uploaded to the media library.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/brand")
    public ApiMessageDto<BrandSettings> saveBrand(@Valid @RequestBody BrandSettings form) {
        return ApiMessageDto.ok(settings.saveBrand(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/currency")
    public ApiMessageDto<CurrencySettingsView> currency() {
        return ApiMessageDto.ok(settings.currency());
    }

    @Operation(summary = "Save currency format", description = "The default currency is chosen on the Currency page.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/currency")
    public ApiMessageDto<CurrencySettingsView> saveCurrency(@Valid @RequestBody CurrencyFormatSettings form) {
        return ApiMessageDto.ok(settings.saveCurrencyFormat(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/seo")
    public ApiMessageDto<SeoSettings> seo() {
        return ApiMessageDto.ok(settings.seo());
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/seo")
    public ApiMessageDto<SeoSettings> saveSeo(@Valid @RequestBody SeoSettings form) {
        return ApiMessageDto.ok(settings.saveSeo(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/cookie")
    public ApiMessageDto<CookieSettings> cookie() {
        return ApiMessageDto.ok(settings.cookie());
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/cookie")
    public ApiMessageDto<CookieSettings> saveCookie(@Valid @RequestBody CookieSettings form) {
        return ApiMessageDto.ok(settings.saveCookie(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/email")
    public ApiMessageDto<EmailSettingsView> email() {
        return ApiMessageDto.ok(settings.email());
    }

    @Operation(summary = "Save Email Settings", description = "password is write-only: omitted keeps the stored one, "
            + "empty string removes it. Stored encrypted.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Encryption key not configured (SETTINGS_002)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/email")
    public ApiMessageDto<EmailSettingsView> saveEmail(@Valid @RequestBody EmailSettingsForm form) {
        return ApiMessageDto.ok(settings.saveEmail(form));
    }

    @Operation(summary = "Send a test email with the saved settings")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Settings incomplete (SETTINGS_003), sending "
            + "failed with the server's reason (SETTINGS_004), secret unreadable (SETTINGS_007)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/admin/email/test")
    public ApiMessageDto<Void> testEmail(@Valid @RequestBody TestEmailForm form) {
        settings.sendTestEmail(form.to());
        return ApiMessageDto.ok(null);
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/payment/bank-transfer")
    public ApiMessageDto<BankTransferSettings> bankTransfer() {
        return ApiMessageDto.ok(settings.bankTransfer());
    }

    @Operation(summary = "Save Bank Transfer details", description = "Shown with every pending plan order. When the "
            + "account fields are empty the SAAS_BANK_* environment values are used.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/payment/bank-transfer")
    public ApiMessageDto<BankTransferSettings> saveBankTransfer(@Valid @RequestBody BankTransferSettings form) {
        return ApiMessageDto.ok(settings.saveBankTransfer(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/storage")
    public ApiMessageDto<StorageSettings> storage() {
        return ApiMessageDto.ok(settings.storage());
    }

    @Operation(summary = "Save Storage Settings", description = "Full replacement. Allowed types must be among the types "
            + "media-service verifies by content; driver LOCAL / S3 / WASABI (omitted = LOCAL) picks where new uploads "
            + "go. media-service applies the change within a minute. S3 credentials are not set here: they are "
            + "media-service's MEDIA_S3_* environment.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unsupported type (SETTINGS_005)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/storage")
    public ApiMessageDto<StorageSettings> saveStorage(@Valid @RequestBody StorageSettings form) {
        return ApiMessageDto.ok(settings.saveStorage(form));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Stripe payment settings",
            description = "Secrets are never returned (secretKeySet, webhookSecretSet).")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/payment/stripe")
    public ApiMessageDto<SettingsDtos.StripeSettingsView> stripe() {
        return ApiMessageDto.ok(settings.stripe());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Save Stripe payment settings",
            description = "Full replacement; secretKey and webhookSecret are write-only (omitted = keep, empty = "
                    + "remove), encrypted with ERP_SETTINGS_ENC_KEY. The webhook URL to register at Stripe is "
                    + "/api/v1/public/payments/stripe/webhook (event checkout.session.completed).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Enabled without all keys (SETTINGS_010), keys "
            + "not of the chosen mode (SETTINGS_011), encryption key not configured (SETTINGS_002), validation"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/payment/stripe")
    public ApiMessageDto<SettingsDtos.StripeSettingsView> saveStripe(
            @Valid @RequestBody SettingsDtos.StripeSettingsForm form) {
        return ApiMessageDto.ok(settings.saveStripe(form));
    }

    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/recaptcha")
    public ApiMessageDto<RecaptchaSettingsView> recaptcha() {
        return ApiMessageDto.ok(settings.recaptcha());
    }

    @Operation(summary = "Save ReCaptcha Settings", description = "When enabled, POST /auth/login and "
            + "/auth/initial-password need `recaptchaToken`. secret is write-only (omitted = keep).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Enabled without site key or secret "
            + "(SETTINGS_006), encryption key not configured (SETTINGS_002)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/recaptcha")
    public ApiMessageDto<RecaptchaSettingsView> saveRecaptcha(@Valid @RequestBody RecaptchaSettingsForm form) {
        return ApiMessageDto.ok(settings.saveRecaptcha(form));
    }

    @Operation(summary = "Platform email notifications",
            description = "New User, New Helpdesk Ticket, New Helpdesk Ticket Reply (each on by default) and the "
                    + "platform team addresses that receive the helpdesk emails.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/email-notifications")
    public ApiMessageDto<SettingsDtos.PlatformEmailNotificationSettings> platformEmailNotifications() {
        return ApiMessageDto.ok(settings.platformEmailNotifications());
    }

    @Operation(summary = "Save platform email notifications", description = "Full replacement. newUser off: no "
            + "welcome email (USER_CREATE) for any company. Helpdesk switches off: helpdeskNotifyEmails get nothing; "
            + "the company side of helpdesk emails keeps the company's own switches. At most 10 addresses.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Validation (COMMON_001): a switch missing, an "
            + "invalid address or more than 10"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/email-notifications")
    public ApiMessageDto<SettingsDtos.PlatformEmailNotificationSettings> savePlatformEmailNotifications(
            @Valid @RequestBody SettingsDtos.PlatformEmailNotificationSettings form) {
        return ApiMessageDto.ok(settings.savePlatformEmailNotifications(form));
    }
}
