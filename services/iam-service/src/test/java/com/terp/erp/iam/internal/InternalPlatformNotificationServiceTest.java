package com.terp.erp.iam.internal;

import com.terp.erp.iam.email.EPlatformEmail;
import com.terp.erp.iam.integration.EPlatformWebhookEvent;
import com.terp.erp.iam.integration.PlatformWebhook;
import com.terp.erp.iam.integration.PlatformWebhookService;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.settings.SettingsDtos.PlatformEmailNotificationSettings;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;
import com.terp.erp.iam.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalPlatformNotificationServiceTest {
    private final SettingsService settings = mock(SettingsService.class);
    private final PlatformWebhookService webhooks = mock(PlatformWebhookService.class);
    private final SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final InternalPlatformNotificationService service =
            new InternalPlatformNotificationService(settings, webhooks, cipher);

    @BeforeEach
    void setUp() {
        SystemSettings system = mock(SystemSettings.class);
        when(system.defaultLanguage()).thenReturn("en");
        when(settings.system()).thenReturn(system);
        when(settings.platformSmtp()).thenReturn(new SettingsService.SmtpConfig("smtp.example.com", 587, "u", "p",
                EMailEncryption.TLS, "noreply@example.com", "ERP"));
        when(settings.platformEmailNotifications()).thenReturn(new PlatformEmailNotificationSettings(true, true, true,
                List.of("support@example.com")));
    }

    @Test
    void switchOnGivesThePlatformSmtpAndTeamAddresses() {
        var cfg = service.config("HELPDESK_TICKET_CREATED", null);

        assertThat(cfg.emailEnabled()).isTrue();
        assertThat(cfg.smtp().host()).isEqualTo("smtp.example.com");
        assertThat(cfg.recipients()).containsExactly("support@example.com");
        assertThat(cfg.webhooks()).isEmpty();
    }

    @Test
    void switchOffGivesNoSmtpAndNoRecipient() {
        when(settings.platformEmailDisabled(EPlatformEmail.HELPDESK_TICKET_REPLY)).thenReturn(true);

        var cfg = service.config("HELPDESK_TICKET_REPLY", null);

        assertThat(cfg.emailEnabled()).isFalse();
        assertThat(cfg.smtp()).isNull();
        assertThat(cfg.recipients()).isEmpty();
        verify(settings, never()).platformSmtp();
    }

    @Test
    void webhooksAreThePlatformOnesWithTheSecretDecrypted() {
        PlatformWebhook w = new PlatformWebhook();
        w.setId(3L);
        w.setEventCode(EPlatformWebhookEvent.NEW_COMPANY);
        w.setUrl("https://hooks.example.com/erp");
        w.setSecretEnc(cipher.encrypt("test-signing-secret-0123"));
        when(webhooks.active(EPlatformWebhookEvent.NEW_COMPANY)).thenReturn(List.of(w));

        var cfg = service.config(null, "NEW_COMPANY");

        assertThat(cfg.emailEnabled()).isFalse();
        assertThat(cfg.webhooks()).singleElement().satisfies(h -> {
            assertThat(h.id()).isEqualTo("3");
            assertThat(h.secret()).isEqualTo("test-signing-secret-0123");
        });
    }
}
