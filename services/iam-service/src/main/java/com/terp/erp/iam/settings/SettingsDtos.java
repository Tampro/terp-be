package com.terp.erp.iam.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Sections of the super admin Settings page (docs/analysis/06 §3.3.2). Records without secrets are stored, edited
 * and returned as they are; Email and ReCaptcha have a stored shape (encrypted secret), a form (plain secret,
 * write-only) and a view (only "is a secret set").
 */
public final class SettingsDtos {
    private SettingsDtos() {
    }

    public enum ESection {
        SYSTEM, BRAND, CURRENCY_FORMAT, SEO, COOKIE, EMAIL, BANK_TRANSFER, STORAGE, RECAPTCHA, STRIPE,
        PLATFORM_EMAIL_NOTIFICATIONS
    }

    // ------------------------------------------------------------------ System

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SystemSettings(
            @Schema(description = "Default Language: code of an enabled language (Language Management)", example = "vi") @NotNull @Pattern(regexp = "[a-z]{2,3}(-[A-Z]{2})?") String defaultLanguage,
            @Schema(description = "Date Format (pattern shown by the FE)", example = "dd/MM/yyyy")
            @NotNull @Pattern(regexp = "dd/MM/yyyy|MM/dd/yyyy|yyyy-MM-dd|dd-MM-yyyy|dd.MM.yyyy") String dateFormat,
            @Schema(description = "Time Format", example = "HH:mm") @NotNull @Pattern(regexp = "HH:mm|hh:mm a|HH:mm:ss") String timeFormat,
            @Schema(description = "Calendar Start Day", example = "MONDAY") @NotNull DayOfWeek calendarStartDay,
            @Schema(description = "Default Timezone (IANA)", example = "Asia/Ho_Chi_Minh") @NotBlank @Size(max = 60) String defaultTimezone,
            @Schema(description = "Email Verification required at registration (applies when self-registration exists)", example = "false")
            boolean emailVerification,
            @Schema(description = "Landing Page enabled", example = "true") boolean landingPage,
            @Schema(description = "User Registration (self sign-up) enabled", example = "false") boolean userRegistration,
            @Schema(description = "Terms and Conditions URL", example = "https://example.com/terms")
            @Size(max = 500) @Pattern(regexp = "^$|https?://.+") String termsUrl,
            @Schema(description = "IP Restriction master switch: off = no company list is enforced; null (settings "
                    + "saved before it existed) = on", example = "true") Boolean ipRestriction) {
        static SystemSettings defaults() {
            return new SystemSettings("vi", "dd/MM/yyyy", "HH:mm", DayOfWeek.MONDAY, "Asia/Ho_Chi_Minh", false, true,
                    false, null, true);
        }

        public boolean ipRestrictionOn() {
            return ipRestriction == null || ipRestriction;
        }
    }

    // ------------------------------------------------------------------ Brand

    public enum ESidebarVariant { INSET, FLOATING, MINIMAL }

    public enum ESidebarStyle { PLAIN, COLORED, GRADIENT }

    public enum ELayoutDirection { LTR, RTL }

    public enum EThemeMode { LIGHT, DARK, SYSTEM }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrandSettings(
            @Schema(description = "Logo (dark mode): URL of an uploaded image (media library)") @Size(max = 1000) String logoDarkUrl,
            @Schema(description = "Logo (light mode)") @Size(max = 1000) String logoLightUrl,
            @Schema(description = "Favicon") @Size(max = 1000) String faviconUrl,
            @Schema(description = "Title Text", example = "ERP") @NotBlank @Size(max = 100) String titleText,
            @Schema(description = "Footer Text", example = "© 2026 TERP") @Size(max = 255) String footerText,
            @Schema(description = "Theme Color: preset name or #RRGGBB", example = "#10b981")
            @NotNull @Pattern(regexp = "blue|green|purple|orange|red|teal|#[0-9a-fA-F]{6}") String themeColor,
            @NotNull ESidebarVariant sidebarVariant,
            @NotNull ESidebarStyle sidebarStyle,
            @NotNull ELayoutDirection layoutDirection,
            @NotNull EThemeMode themeMode,
            @Schema(description = "Company Mobile Number", example = "+84 28 0000 0000")
            @Size(max = 30) @Pattern(regexp = "^$|[0-9+()\\-. ]{3,30}") String companyMobile,
            @Schema(description = "Company Address (footer, contact sections)") @Size(max = 500) String companyAddress) {
        static BrandSettings defaults() {
            return new BrandSettings(null, null, null, "ERP", null, "blue", ESidebarVariant.INSET,
                    ESidebarStyle.PLAIN, ELayoutDirection.LTR, EThemeMode.LIGHT, null, null);
        }
    }

    // ------------------------------------------------------------------ Currency format

    public enum ESymbolPosition { BEFORE, AFTER }

    public enum ESeparator { DOT, COMMA, SPACE, NONE }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrencyFormatSettings(
            @Schema(description = "Decimal Places", example = "0") @Min(0) @Max(4) int decimalPlaces,
            @NotNull ESymbolPosition symbolPosition,
            @Schema(description = "Decimal Separator: DOT or COMMA") @NotNull ESeparator decimalSeparator,
            @Schema(description = "Thousands Separator") @NotNull ESeparator thousandsSeparator,
            @Schema(description = "Show Decimals") boolean showDecimals,
            @Schema(description = "Add Space between amount and symbol") boolean addSpace) {
        static CurrencyFormatSettings defaults() {
            // VND: 490.000 ₫
            return new CurrencyFormatSettings(0, ESymbolPosition.AFTER, ESeparator.COMMA, ESeparator.DOT, false, true);
        }
    }

    @Schema(description = "Currency Settings view: the format plus the default currency (managed on the Currency page)")
    public record CurrencySettingsView(CurrencyFormatSettings format, String defaultCurrencyCode,
                                       String defaultCurrencySymbol) {
    }

    // ------------------------------------------------------------------ SEO, Cookie

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeoSettings(
            @Schema(description = "Meta Keywords, comma separated", example = "erp, payroll, attendance") @Size(max = 500) String metaKeywords,
            @Schema(description = "Meta Description (150–160 characters recommended)") @Size(max = 500) String metaDescription,
            @Schema(description = "Meta Image URL (1200×630 recommended)") @Size(max = 1000) String metaImageUrl) {
        static SeoSettings defaults() {
            return new SeoSettings(null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CookieSettings(
            @Schema(description = "Enable Logging (of consent choices)") boolean enableLogging,
            @Size(max = 200) String cookieTitle,
            @Size(max = 2000) String cookieDescription,
            @Size(max = 200) String strictlyTitle,
            @Size(max = 2000) String strictlyDescription,
            @Size(max = 2000) String contactDescription,
            @Size(max = 500) @Pattern(regexp = "^$|https?://.+") String contactUrl) {
        static CookieSettings defaults() {
            return new CookieSettings(false, "Cookie Consent", null, "Strictly Necessary Cookies",
                    "These cookies are essential for the website to function and cannot be switched off.", null, null);
        }
    }

    // ------------------------------------------------------------------ Email (secret)

    public enum EMailEncryption { NONE, SSL, TLS }

    /** Stored shape: the password only as AES-GCM ciphertext. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailSettings(String host, Integer port, String username, String passwordEnc,
                                EMailEncryption encryption, String fromAddress, String fromName) {
        static EmailSettings defaults() {
            return new EmailSettings(null, 587, null, null, EMailEncryption.TLS, null, null);
        }
    }

    public record EmailSettingsForm(
            @Schema(description = "SMTP Host", example = "smtp.example.com") @NotBlank @Size(max = 255) String host,
            @Schema(description = "SMTP Port", example = "587") @NotNull @Min(1) @Max(65535) Integer port,
            @Schema(description = "SMTP Username") @Size(max = 255) String username,
            @Schema(description = "SMTP Password — write-only. Omitted = keep the stored one; empty string = remove it.")
            @Size(max = 255) String password,
            @NotNull EMailEncryption encryption,
            @Schema(description = "From Address", example = "noreply@example.com") @NotBlank @Email String fromAddress,
            @Schema(description = "From Name", example = "ERP") @Size(max = 100) String fromName) {
    }

    public record EmailSettingsView(String host, Integer port, String username, boolean passwordSet,
                                    EMailEncryption encryption, String fromAddress, String fromName) {
        static EmailSettingsView of(EmailSettings s) {
            return new EmailSettingsView(s.host(), s.port(), s.username(), s.passwordEnc() != null, s.encryption(),
                    s.fromAddress(), s.fromName());
        }
    }

    public record TestEmailForm(@Schema(description = "Send Test To", example = "me@example.com") @NotBlank @Email String to) {
    }

    // ------------------------------------------------------------------ Bank transfer

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BankTransferSettings(
            @Schema(description = "Bank Transfer offered on plan orders", example = "true") boolean enabled,
            @Schema(description = "Bank name", example = "Vietcombank") @Size(max = 100) String bankName,
            @Schema(description = "Account number", example = "0123456789") @Size(max = 50) String accountNumber,
            @Schema(description = "Account holder", example = "CONG TY TERP") @Size(max = 100) String accountName,
            @Schema(description = "Other details shown to companies (branch, SWIFT…)") @Size(max = 1000) String details) {
        static BankTransferSettings defaults() {
            return new BankTransferSettings(true, null, null, null, null);
        }
    }

    // ------------------------------------------------------------------ Storage

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StorageSettings(
            @Schema(description = "Allowed File Types (extensions). Only types media-service can verify are accepted.",
                    example = "[\"png\",\"jpg\",\"pdf\"]") @NotNull @Size(min = 1) List<@NotBlank String> allowedFileTypes,
            @Schema(description = "Max Upload Size (KB)", example = "10240") @Min(1) @Max(1024 * 1024) int maxUploadSizeKb) {
        /** Every type media-service can check by its content (FileTypePolicy). */
        public static final List<String> SUPPORTED = List.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "doc", "docx",
                "xls", "xlsx", "pptx", "txt", "csv");

        static StorageSettings defaults() {
            return new StorageSettings(SUPPORTED, 10240);
        }
    }

    // ------------------------------------------------------------------ ReCaptcha (secret)

    public enum ERecaptchaVersion { V2, V3 }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecaptchaSettings(boolean enabled, ERecaptchaVersion version, String siteKey, String secretEnc) {
        static RecaptchaSettings defaults() {
            return new RecaptchaSettings(false, ERecaptchaVersion.V2, null, null);
        }
    }

    public record RecaptchaSettingsForm(
            @Schema(description = "Enable ReCaptcha on login and first-login password", example = "true") boolean enabled,
            @NotNull ERecaptchaVersion version,
            @Schema(description = "Site Key (public)") @Size(max = 200) String siteKey,
            @Schema(description = "Secret Key — write-only. Omitted = keep the stored one.") @Size(max = 200) String secret) {
    }

    public record RecaptchaSettingsView(boolean enabled, ERecaptchaVersion version, String siteKey, boolean secretSet) {
        static RecaptchaSettingsView of(RecaptchaSettings s) {
            return new RecaptchaSettingsView(s.enabled(), s.version(), s.siteKey(), s.secretEnc() != null);
        }
    }

    // ------------------------------------------------------------------ Stripe (docs/analysis/06 §3.3.2 Payment)

    public enum EStripeMode { TEST, LIVE }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StripeSettings(boolean enabled, EStripeMode mode, String publishableKey, String secretKeyEnc,
                                 String webhookSecretEnc) {
        static StripeSettings defaults() {
            return new StripeSettings(false, EStripeMode.TEST, null, null, null);
        }
    }

    public record StripeSettingsForm(
            @Schema(description = "Offer Stripe Checkout on plan orders", example = "true") boolean enabled,
            @Schema(description = "TEST or LIVE: the keys must be of that mode") @NotNull EStripeMode mode,
            @Schema(description = "Publishable key (public)", example = "pk_test_xxx")
            @Size(max = 255) @Pattern(regexp = "^$|pk_(test|live)_[A-Za-z0-9]+") String publishableKey,
            @Schema(description = "Secret key — write-only. Omitted = keep; empty = remove.") @Size(max = 255) String secretKey,
            @Schema(description = "Webhook signing secret (whsec_…) — write-only. Omitted = keep; empty = remove.")
            @Size(max = 255) String webhookSecret) {
    }

    public record StripeSettingsView(boolean enabled, EStripeMode mode, String publishableKey, boolean secretKeySet,
                                     boolean webhookSecretSet) {
        static StripeSettingsView of(StripeSettings s) {
            return new StripeSettingsView(s.enabled(), s.mode(), s.publishableKey(), s.secretKeyEnc() != null,
                    s.webhookSecretEnc() != null);
        }
    }

    @Schema(description = "Payment methods a company can choose on a plan order")
    public record PublicPayments(boolean bankTransfer, boolean stripe, String stripePublishableKey) {
    }

    // ------------------------------------------------------------------ public bundle

    @Schema(description = "Everything the FE may show before login. No secret, no internal value.")
    public record PublicSettings(SystemSettings system, BrandSettings brand, CurrencySettingsView currency,
                                 SeoSettings seo, CookieSettings cookie, PublicRecaptcha recaptcha,
                                 PublicPayments payments) {
    }

    public record PublicRecaptcha(boolean enabled, ERecaptchaVersion version, String siteKey) {
    }

    // ------------------------------------------------------------------ Platform email notifications

    /**
     * The super admin's Email Notifications (ERP manual 3.7: New User, New Helpdesk Ticket, New Helpdesk Ticket
     * Reply); every switch defaults to on. newUser governs the USER_CREATE email IAM sends for every company (the
     * company's own switch still applies on top); the helpdesk switches govern the emails to helpdeskNotifyEmails,
     * the platform team's addresses (the company side of helpdesk emails keeps its own switches).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlatformEmailNotificationSettings(
            @Schema(description = "New User: welcome email with the temporary password", example = "true")
            @NotNull Boolean newUser,
            @Schema(description = "New Helpdesk Ticket: email to helpdeskNotifyEmails", example = "true")
            @NotNull Boolean helpdeskTicketCreated,
            @Schema(description = "New Helpdesk Ticket Reply (a company replied): email to helpdeskNotifyEmails",
                    example = "true") @NotNull Boolean helpdeskTicketReply,
            @Schema(description = "Platform team addresses for helpdesk emails, at most 10", example =
                    "[\"support@example.com\"]") @NotNull @Size(max = 10)
            List<@NotBlank @Email @Size(max = 254) String> helpdeskNotifyEmails) {
        static PlatformEmailNotificationSettings defaults() {
            return new PlatformEmailNotificationSettings(true, true, true, List.of());
        }
    }
}
