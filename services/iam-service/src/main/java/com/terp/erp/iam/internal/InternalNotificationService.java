package com.terp.erp.iam.internal;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.email.CompanyEmailNotification;
import com.terp.erp.iam.email.CompanyEmailNotificationRepository;
import com.terp.erp.iam.email.EEmailError;
import com.terp.erp.iam.email.ENotificationEvent;
import com.terp.erp.iam.integration.CompanyIntegrationService;
import com.terp.erp.iam.integration.CompanyWebhookRepository;
import com.terp.erp.iam.integration.EWebhookEvent;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.settings.CompanyEmailService;
import com.terp.erp.iam.settings.CompanySettingsService;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.settings.SettingsService.SmtpConfig;
import com.terp.erp.common.Nulls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * What notification-service needs to deliver one event of one company: the email switch, the SMTP that sends the
 * company's emails, the Slack / Telegram integrations when on, and the active webhooks of the event — secrets
 * decrypted. Reached only through the internal chain (service-account token of an allowed client); never logged.
 */
@Service
@RequiredArgsConstructor
public class InternalNotificationService {
    public record Smtp(String host, int port, String username, String password, EMailEncryption encryption,
                       String fromAddress, String fromName) {
    }

    public record Slack(String webhookUrl) {
    }

    public record Telegram(String botToken, String chatId) {
    }

    /** secret null = unsigned webhook. */
    public record Webhook(String id, String url, String secret) {
    }

    /** emailEnabled: the company's switch of eventCode (no row = on; no eventCode = false). */
    public record NotificationConfig(String companyId, String defaultLanguage, boolean emailEnabled, Smtp smtp,
                                     Slack slack, Telegram telegram, List<Webhook> webhooks) {
    }

    public record Contact(String id, String fullName, String email, String companyId, boolean active) {
    }

    private final CompanyRepository companies;
    private final CompanyEmailNotificationRepository toggles;
    private final CompanyEmailService companyEmail;
    private final CompanySettingsService companySettings;
    private final CompanyIntegrationService integrations;
    private final CompanyWebhookRepository webhooks;
    private final UserAccountRepository users;
    private final SecretCipher cipher;

    @Transactional(readOnly = true)
    public NotificationConfig config(Long companyId, String eventCode, String webhookEvent) {
        if (companyId == null || !companies.existsById(Nulls.nonNull(companyId))) {
            throw ApiException.notFound(EIamError.IAM_001);
        }
        boolean emailEnabled = false;
        if (eventCode != null && !eventCode.isBlank()) {
            ENotificationEvent e = Arrays.stream(ENotificationEvent.values()).filter(x -> x.name().equals(eventCode))
                    .findFirst().orElseThrow(() -> ApiException.badRequest(EEmailError.EMAIL_001));
            emailEnabled = toggles.findByCompanyIdAndTemplateCode(companyId, e.name())
                    .map(CompanyEmailNotification::isEnabled).orElse(true);
        }
        List<Webhook> hooks = List.of();
        if (webhookEvent != null && !webhookEvent.isBlank()) {
            EWebhookEvent w = Arrays.stream(EWebhookEvent.values()).filter(x -> x.name().equals(webhookEvent))
                    .findFirst().orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_014, webhookEvent));
            hooks = webhooks.findByCompanyIdAndEventCodeAndActiveTrueAndDeletedFalseOrderByIdAsc(companyId, w).stream()
                    .map(h -> new Webhook(String.valueOf(h.getId()), h.getUrl(),
                            h.getSecretEnc() == null ? null : cipher.decrypt(h.getSecretEnc())))
                    .toList();
        }
        SmtpConfig s = emailEnabled ? companyEmail.smtpFor(companyId) : null;
        Smtp smtp = s == null ? null : new Smtp(s.host(), s.port(), s.username(), s.password(), s.encryption(),
                s.fromAddress(), s.fromName());
        Slack slack = integrations.slackWebhookUrl(companyId).map(Slack::new).orElse(null);
        Telegram telegram = integrations.telegramTarget(companyId)
                .map(t -> new Telegram(t.botToken(), t.chatId())).orElse(null);
        return new NotificationConfig(String.valueOf(companyId), companySettings.defaultLanguageOf(companyId),
                emailEnabled, smtp, slack, telegram, hooks);
    }

    @Transactional(readOnly = true)
    public Contact contact(Long userId) {
        UserAccount u = users.findByIdAndDeletedFalse(userId).orElseThrow(() -> ApiException.notFound(EIamError.IAM_009));
        return new Contact(String.valueOf(u.getId()), u.getFullName(), u.getEmail(),
                u.getCompanyId() == null ? null : String.valueOf(u.getCompanyId()),
                u.getStatus() == com.terp.erp.iam.constant.EUserStatus.ACTIVE);
    }
}
