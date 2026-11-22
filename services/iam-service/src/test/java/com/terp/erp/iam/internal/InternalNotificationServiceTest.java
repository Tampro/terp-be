package com.terp.erp.iam.internal;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.email.CompanyEmailNotification;
import com.terp.erp.iam.email.CompanyEmailNotificationRepository;
import com.terp.erp.iam.integration.CompanyIntegrationService;
import com.terp.erp.iam.integration.CompanyWebhook;
import com.terp.erp.iam.integration.CompanyWebhookRepository;
import com.terp.erp.iam.integration.EWebhookEvent;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.settings.CompanyEmailService;
import com.terp.erp.iam.settings.CompanySettingsService;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.settings.SettingsService.SmtpConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalNotificationServiceTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyEmailNotificationRepository toggles = mock(CompanyEmailNotificationRepository.class);
    private final CompanyEmailService companyEmail = mock(CompanyEmailService.class);
    private final CompanySettingsService settings = mock(CompanySettingsService.class);
    private final CompanyIntegrationService integrations = mock(CompanyIntegrationService.class);
    private final CompanyWebhookRepository webhooks = mock(CompanyWebhookRepository.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final InternalNotificationService service = new InternalNotificationService(companies, toggles,
            companyEmail, settings, integrations, webhooks, mock(UserAccountRepository.class), cipher);

    @Test
    void returnsTheChannelsOfTheCompanyWithSecretsDecrypted() {
        when(companies.existsById(7L)).thenReturn(true);
        when(settings.defaultLanguageOf(7L)).thenReturn("en");
        when(companyEmail.smtpFor(7L)).thenReturn(new SmtpConfig("smtp.example.com", 587, "u", "pw",
                EMailEncryption.TLS, "noreply@example.com", "Test Co"));
        when(integrations.slackWebhookUrl(7L)).thenReturn(Optional.of("https://hooks.slack.com/services/x"));
        when(integrations.telegramTarget(7L)).thenReturn(Optional.empty());
        CompanyWebhook w = new CompanyWebhook();
        w.setId(3L);
        w.setUrl("https://hooks.example.com/erp");
        w.setSecretEnc("enc");
        when(cipher.decrypt("enc")).thenReturn("a-long-signing-secret");
        when(webhooks.findByCompanyIdAndEventCodeAndActiveTrueAndDeletedFalseOrderByIdAsc(7L,
                EWebhookEvent.NEW_SALES_INVOICE)).thenReturn(List.of(w));

        var c = service.config(7L, "INVOICE_SENT", "NEW_SALES_INVOICE");

        assertThat(c.emailEnabled()).isTrue();
        assertThat(c.defaultLanguage()).isEqualTo("en");
        assertThat(c.smtp().password()).isEqualTo("pw");
        assertThat(c.slack().webhookUrl()).isEqualTo("https://hooks.slack.com/services/x");
        assertThat(c.telegram()).isNull();
        assertThat(c.webhooks()).singleElement().satisfies(h -> {
            assertThat(h.id()).isEqualTo("3");
            assertThat(h.secret()).isEqualTo("a-long-signing-secret");
        });
    }

    @Test
    void aSwitchedOffEmailHasNoSmtp() {
        when(companies.existsById(7L)).thenReturn(true);
        CompanyEmailNotification off = new CompanyEmailNotification();
        off.setEnabled(false);
        when(toggles.findByCompanyIdAndTemplateCode(7L, "LEAD_ASSIGNED")).thenReturn(Optional.of(off));
        when(integrations.slackWebhookUrl(7L)).thenReturn(Optional.empty());
        when(integrations.telegramTarget(7L)).thenReturn(Optional.empty());

        var c = service.config(7L, "LEAD_ASSIGNED", null);

        assertThat(c.emailEnabled()).isFalse();
        assertThat(c.smtp()).isNull();
        assertThat(c.webhooks()).isEmpty();
        verify(companyEmail, never()).smtpFor(7L);
        verify(cipher, never()).decrypt(anyString());
    }

    @Test
    void anUnknownCompanyIs404() {
        assertThatThrownBy(() -> service.config(99L, null, null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(EIamError.IAM_001));
    }
}
