package com.terp.erp.iam.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.repository.CurrencyRepository;
import com.terp.erp.iam.settings.SettingsDtos.BankTransferSettings;
import com.terp.erp.iam.settings.SettingsDtos.BrandSettings;
import com.terp.erp.iam.settings.SettingsDtos.CookieSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencyFormatSettings;
import com.terp.erp.iam.settings.SettingsDtos.CurrencySettingsView;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.settings.SettingsDtos.ESection;
import com.terp.erp.iam.settings.SettingsDtos.EmailSettings;
import com.terp.erp.iam.settings.SettingsDtos.EmailSettingsForm;
import com.terp.erp.iam.settings.SettingsDtos.EmailSettingsView;
import com.terp.erp.iam.settings.SettingsDtos.PublicRecaptcha;
import com.terp.erp.iam.settings.SettingsDtos.PublicSettings;
import com.terp.erp.iam.settings.SettingsDtos.RecaptchaSettings;
import com.terp.erp.iam.settings.SettingsDtos.RecaptchaSettingsForm;
import com.terp.erp.iam.settings.SettingsDtos.RecaptchaSettingsView;
import com.terp.erp.iam.settings.SettingsDtos.SeoSettings;
import com.terp.erp.iam.settings.SettingsDtos.StorageSettings;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;
import com.terp.erp.security.CurrentUser;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Platform settings (docs/analysis/06 §3.3.2), one JSON row per section; a section never saved reads as its
 * defaults. PUT is a full replacement of a section, except write-only secrets: omitted = keep.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {
    private final PlatformSettingRepository repo;
    private final CurrencyRepository currencies;
    private final com.terp.erp.iam.language.LanguageRepository languages;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;

    // ------------------------------------------------------------------ plain sections

    @Transactional(readOnly = true)
    public SystemSettings system() {
        return read(ESection.SYSTEM, SystemSettings.class, SystemSettings::defaults);
    }

    @Transactional
    public SystemSettings saveSystem(SystemSettings s) {
        try {
            ZoneId.of(s.defaultTimezone());
        } catch (DateTimeException e) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_001, s.defaultTimezone());
        }
        if (!languages.findByCode(s.defaultLanguage()).map(l -> l.isEnabled()).orElse(false)) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_008, s.defaultLanguage());
        }
        return write(ESection.SYSTEM, s);
    }

    @Transactional(readOnly = true)
    public BrandSettings brand() {
        return read(ESection.BRAND, BrandSettings.class, BrandSettings::defaults);
    }

    @Transactional
    public BrandSettings saveBrand(BrandSettings s) {
        return write(ESection.BRAND, s);
    }

    @Transactional(readOnly = true)
    public CurrencySettingsView currency() {
        CurrencyFormatSettings f = read(ESection.CURRENCY_FORMAT, CurrencyFormatSettings.class,
                CurrencyFormatSettings::defaults);
        var def = currencies.findFirstByDefaultCurrencyTrue();
        return new CurrencySettingsView(f, def.map(c -> c.getCode()).orElse(null),
                def.map(c -> c.getSymbol()).orElse(null));
    }

    /** The default currency itself is changed on the Currency page (one source of truth). */
    @Transactional
    public CurrencySettingsView saveCurrencyFormat(CurrencyFormatSettings s) {
        write(ESection.CURRENCY_FORMAT, s);
        return currency();
    }

    @Transactional(readOnly = true)
    public SeoSettings seo() {
        return read(ESection.SEO, SeoSettings.class, SeoSettings::defaults);
    }

    @Transactional
    public SeoSettings saveSeo(SeoSettings s) {
        return write(ESection.SEO, s);
    }

    @Transactional(readOnly = true)
    public CookieSettings cookie() {
        return read(ESection.COOKIE, CookieSettings.class, CookieSettings::defaults);
    }

    @Transactional
    public CookieSettings saveCookie(CookieSettings s) {
        return write(ESection.COOKIE, s);
    }

    @Transactional(readOnly = true)
    public BankTransferSettings bankTransfer() {
        return read(ESection.BANK_TRANSFER, BankTransferSettings.class, BankTransferSettings::defaults);
    }

    @Transactional
    public BankTransferSettings saveBankTransfer(BankTransferSettings s) {
        return write(ESection.BANK_TRANSFER, s);
    }

    @Transactional(readOnly = true)
    public StorageSettings storage() {
        return read(ESection.STORAGE, StorageSettings.class, StorageSettings::defaults);
    }

    /** Only types media-service can verify by content may be allowed (it refuses the rest anyway). */
    @Transactional
    public StorageSettings saveStorage(StorageSettings s) {
        List<String> types = s.allowedFileTypes().stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        TreeSet<String> unsupported = new TreeSet<>(types);
        unsupported.removeAll(StorageSettings.SUPPORTED);
        if (!unsupported.isEmpty()) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_005, String.join(", ", unsupported));
        }
        return write(ESection.STORAGE, new StorageSettings(types, s.maxUploadSizeKb()));
    }

    // ------------------------------------------------------------------ email (secret)

    @Transactional(readOnly = true)
    public EmailSettingsView email() {
        return EmailSettingsView.of(emailStored());
    }

    @Transactional
    public EmailSettingsView saveEmail(EmailSettingsForm f) {
        String enc = emailStored().passwordEnc();
        if (f.password() != null) {
            enc = f.password().isEmpty() ? null : cipher.encrypt(f.password());
        }
        return EmailSettingsView.of(write(ESection.EMAIL, new EmailSettings(f.host().trim(), f.port(), f.username(),
                enc, f.encryption(), f.fromAddress(), f.fromName())));
    }

    /**
     * Decrypted SMTP settings for notification-service (internal endpoint only, never returned to a user): host,
     * port, credentials and sender identity. null when not configured.
     */
    public record SmtpConfig(String host, int port, String username, String password, EMailEncryption encryption,
                             String fromAddress, String fromName) {
    }

    @Transactional(readOnly = true)
    public SmtpConfig platformSmtp() {
        EmailSettings s = emailStored();
        if (s.host() == null || s.host().isBlank() || s.fromAddress() == null) {
            return null;
        }
        return new SmtpConfig(s.host(), s.port() == null ? 587 : s.port(), s.username(),
                s.passwordEnc() == null ? null : cipher.decrypt(s.passwordEnc()), s.encryption(), s.fromAddress(),
                s.fromName());
    }

    /** A ready SMTP sender with the stored settings and the sender identity, or null when not configured. */
    public record Mailer(JavaMailSenderImpl sender, String fromAddress, String fromName) {
    }

    @Transactional(readOnly = true)
    public Mailer mailer() {
        EmailSettings s = emailStored();
        if (s.host() == null || s.host().isBlank() || s.fromAddress() == null) {
            return null;
        }
        return buildMailer(s.host(), s.port(), s.username(),
                s.passwordEnc() == null ? null : cipher.decrypt(s.passwordEnc()), s.encryption(), s.fromAddress(),
                s.fromName());
    }

    /** An SMTP sender with explicit timeouts (shared by the platform and the company email settings). */
    public static Mailer buildMailer(String host, Integer port, String username, String password,
                                     EMailEncryption encryption, String fromAddress, String fromName) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port == null ? 587 : port);
        sender.setDefaultEncoding("UTF-8");
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(sender.getUsername() != null));
        p.put("mail.smtp.starttls.enable", String.valueOf(encryption == EMailEncryption.TLS));
        p.put("mail.smtp.ssl.enable", String.valueOf(encryption == EMailEncryption.SSL));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return new Mailer(sender, fromAddress, fromName);
    }

    /** Sends one message with the stored settings; the error text of the mail server is returned, never a secret. */
    @Transactional(readOnly = true)
    public void sendTestEmail(String to) {
        sendTest(mailer(), to, "This is a test email from the ERP platform settings.");
    }

    /** SETTINGS_003 when not configured, SETTINGS_004 with the server's first error line when sending fails. */
    public static void sendTest(Mailer mailer, String to, String text) {
        send(mailer, to, "ERP — test email", text, ESettingsError.SETTINGS_004);
    }

    /**
     * A plain-text email; SETTINGS_003 when not configured, `failed` (with the server's reason as {0}) when the
     * server refuses it, so each caller reports its own action (test email, inquiry reply).
     */
    public static void send(Mailer mailer, String to, String subject, String text,
                            com.terp.erp.exception.ErrorCode failed) {
        if (mailer == null) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_003);
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(mailer.fromName() == null ? mailer.fromAddress() : mailer.fromName() + " <" + mailer.fromAddress() + ">");
        m.setTo(to);
        m.setSubject(subject);
        m.setText(text);
        try {
            mailer.sender().send(m);
        } catch (MailException e) {
            String reason = e.getMostSpecificCause().getMessage();
            log.warn("Email failed: {}", e.getClass().getSimpleName());
            throw ApiException.badRequest(failed,
                    reason == null ? e.getClass().getSimpleName() : reason.lines().findFirst().orElse(""));
        }
    }

    private EmailSettings emailStored() {
        return read(ESection.EMAIL, EmailSettings.class, EmailSettings::defaults);
    }

    // ------------------------------------------------------------------ reCAPTCHA (secret)

    @Transactional(readOnly = true)
    public RecaptchaSettingsView recaptcha() {
        return RecaptchaSettingsView.of(recaptchaStored());
    }

    @Transactional
    public RecaptchaSettingsView saveRecaptcha(RecaptchaSettingsForm f) {
        String enc = recaptchaStored().secretEnc();
        if (f.secret() != null) {
            enc = f.secret().isEmpty() ? null : cipher.encrypt(f.secret());
        }
        if (f.enabled() && (enc == null || f.siteKey() == null || f.siteKey().isBlank())) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_006);
        }
        return RecaptchaSettingsView.of(write(ESection.RECAPTCHA,
                new RecaptchaSettings(f.enabled(), f.version(), f.siteKey(), enc)));
    }

    /** For the login check: the stored settings with the decrypted secret; null when disabled. */
    @Transactional(readOnly = true)
    public ActiveRecaptcha activeRecaptcha() {
        RecaptchaSettings s = recaptchaStored();
        return s.enabled() ? new ActiveRecaptcha(s.version(), cipher.decrypt(s.secretEnc())) : null;
    }

    public record ActiveRecaptcha(SettingsDtos.ERecaptchaVersion version, String secret) {
    }

    private RecaptchaSettings recaptchaStored() {
        return read(ESection.RECAPTCHA, RecaptchaSettings.class, RecaptchaSettings::defaults);
    }

    // ------------------------------------------------------------------ Stripe

    @Transactional(readOnly = true)
    public SettingsDtos.StripeSettingsView stripe() {
        return SettingsDtos.StripeSettingsView.of(stripeStored());
    }

    /** Secrets are write-only: omitted = keep, empty = remove. Enabled needs all three keys, of the chosen mode. */
    @Transactional
    public SettingsDtos.StripeSettingsView saveStripe(SettingsDtos.StripeSettingsForm f) {
        SettingsDtos.StripeSettings old = stripeStored();
        String secret = old.secretKeyEnc();
        if (f.secretKey() != null) {
            secret = f.secretKey().isEmpty() ? null : cipher.encrypt(f.secretKey().trim());
        }
        String hook = old.webhookSecretEnc();
        if (f.webhookSecret() != null) {
            hook = f.webhookSecret().isEmpty() ? null : cipher.encrypt(f.webhookSecret().trim());
        }
        String pk = f.publishableKey() == null || f.publishableKey().isBlank() ? null : f.publishableKey().trim();
        if (f.enabled() && (pk == null || secret == null || hook == null)) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_010);
        }
        String prefix = f.mode() == SettingsDtos.EStripeMode.LIVE ? "live_" : "test_";
        if ((pk != null && !pk.startsWith("pk_" + prefix))
                || (f.secretKey() != null && !f.secretKey().isEmpty() && !f.secretKey().trim().startsWith("sk_" + prefix)
                && !f.secretKey().trim().startsWith("rk_" + prefix))) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_011, f.mode());
        }
        return SettingsDtos.StripeSettingsView.of(write(ESection.STRIPE,
                new SettingsDtos.StripeSettings(f.enabled(), f.mode(), pk, secret, hook)));
    }

    /** For checkout and the webhook: the decrypted keys; null while Stripe is off. Never logged. */
    @Transactional(readOnly = true)
    public ActiveStripe activeStripe() {
        SettingsDtos.StripeSettings s = stripeStored();
        if (!s.enabled() || s.secretKeyEnc() == null || s.webhookSecretEnc() == null) {
            return null;
        }
        return new ActiveStripe(cipher.decrypt(s.secretKeyEnc()), cipher.decrypt(s.webhookSecretEnc()), s.mode());
    }

    public record ActiveStripe(String secretKey, String webhookSecret, SettingsDtos.EStripeMode mode) {
        @Override
        public String toString() {
            return "ActiveStripe[" + mode + "]";
        }
    }

    private SettingsDtos.StripeSettings stripeStored() {
        return read(ESection.STRIPE, SettingsDtos.StripeSettings.class, SettingsDtos.StripeSettings::defaults);
    }

    // ------------------------------------------------------------------ public bundle

    @Transactional(readOnly = true)
    public PublicSettings publicSettings() {
        RecaptchaSettings r = recaptchaStored();
        return new PublicSettings(system(), brand(), currency(), seo(), cookie(),
                new PublicRecaptcha(r.enabled(), r.version(), r.enabled() ? r.siteKey() : null), payments());
    }

    private SettingsDtos.PublicPayments payments() {
        SettingsDtos.StripeSettings st = stripeStored();
        boolean stripe = st.enabled() && st.secretKeyEnc() != null && st.webhookSecretEnc() != null;
        return new SettingsDtos.PublicPayments(bankTransfer().enabled(), stripe, stripe ? st.publishableKey() : null);
    }

    // ------------------------------------------------------------------ storage of a section

    private <T> T read(ESection section, Class<T> type, Supplier<T> defaults) {
        return repo.findById(section.name()).map(row -> {
            try {
                return mapper.readValue(row.getValueJson(), type);
            } catch (JsonProcessingException e) {
                // A row the current code cannot read: fall back rather than break the settings page.
                log.error("Settings section {} unreadable, using defaults", section);
                return defaults.get();
            }
        }).orElseGet(defaults);
    }

    private <T> T write(ESection section, T value) {
        PlatformSetting row = repo.findById(section.name()).orElseGet(() -> {
            PlatformSetting p = new PlatformSetting();
            p.setSection(section.name());
            return p;
        });
        try {
            row.setValueJson(mapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise settings " + section, e);
        }
        row.setUpdatedBy(CurrentUser.find().map(p -> p.keycloakId()).orElse("system"));
        row.setUpdatedDate(clock.instant());
        repo.save(row);
        return value;
    }
}
