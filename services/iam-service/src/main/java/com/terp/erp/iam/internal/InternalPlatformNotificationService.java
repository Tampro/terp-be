package com.terp.erp.iam.internal;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.email.EEmailError;
import com.terp.erp.iam.email.EPlatformEmail;
import com.terp.erp.iam.integration.EPlatformWebhookEvent;
import com.terp.erp.iam.integration.PlatformWebhookService;
import com.terp.erp.iam.internal.InternalNotificationService.Smtp;
import com.terp.erp.iam.internal.InternalNotificationService.Webhook;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.iam.settings.SettingsService;
import com.terp.erp.iam.settings.SettingsService.SmtpConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * What notification-service needs to deliver one platform event (companyId null): the super admin's email switch,
 * the platform SMTP, the platform team addresses and the active platform webhooks of the event — secrets decrypted.
 * Never a company's configuration. Reached only through the internal chain; never logged.
 */
@Service
@RequiredArgsConstructor
public class InternalPlatformNotificationService {
    /**
     * emailEnabled: the platform switch of emailCode (no emailCode = false). smtp: the platform SMTP, null when the
     * switch is off or SMTP is not configured. recipients: helpdeskNotifyEmails when the switch is on, else empty.
     */
    public record PlatformNotificationConfig(String defaultLanguage, boolean emailEnabled, Smtp smtp,
                                             List<String> recipients, List<Webhook> webhooks) {
    }

    private final SettingsService settings;
    private final PlatformWebhookService webhooks;
    private final SecretCipher cipher;

    @Transactional(readOnly = true)
    public PlatformNotificationConfig config(String emailCode, String webhookEvent) {
        boolean emailEnabled = false;
        if (emailCode != null && !emailCode.isBlank()) {
            EPlatformEmail e = Arrays.stream(EPlatformEmail.values()).filter(x -> x.name().equals(emailCode))
                    .findFirst().orElseThrow(() -> ApiException.badRequest(EEmailError.EMAIL_001));
            emailEnabled = !settings.platformEmailDisabled(e);
        }
        List<Webhook> hooks = List.of();
        if (webhookEvent != null && !webhookEvent.isBlank()) {
            EPlatformWebhookEvent w = PlatformWebhookService.event(webhookEvent);
            hooks = webhooks.active(w).stream()
                    .map(h -> new Webhook(String.valueOf(h.getId()), h.getUrl(),
                            h.getSecretEnc() == null ? null : cipher.decrypt(h.getSecretEnc())))
                    .toList();
        }
        SmtpConfig s = emailEnabled ? settings.platformSmtp() : null;
        Smtp smtp = s == null ? null : new Smtp(s.host(), s.port(), s.username(), s.password(), s.encryption(),
                s.fromAddress(), s.fromName());
        List<String> recipients = emailEnabled ? settings.platformEmailNotifications().helpdeskNotifyEmails()
                : List.of();
        return new PlatformNotificationConfig(settings.system().defaultLanguage(), emailEnabled, smtp, recipients,
                hooks);
    }
}
