package com.terp.erp.iam.email;

import org.junit.jupiter.api.Test;
import com.terp.erp.iam.settings.SettingsService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailTemplatesTest {

    @Test
    void valuesAreHtmlEscapedInTheBody() {
        String html = TemplateRenderer.html("<p>Hi {user_name} of {company_name}</p>",
                Map.of("user_name", "Test", "company_name", "<script>alert(1)</script>"));
        assertThat(html).isEqualTo("<p>Hi Test of &lt;script&gt;alert(1)&lt;/script&gt;</p>");
    }

    @Test
    void subjectCannotCarryAHeaderBreak() {
        String subject = TemplateRenderer.line("Order {order_number}", Map.of("order_number", "X\r\nBcc: a@b.c"));
        assertThat(subject).doesNotContain("\n").doesNotContain("\r").isEqualTo("Order X Bcc: a@b.c");
    }

    @Test
    void unknownPlaceholderStaysVisible() {
        assertThat(TemplateRenderer.html("{typo_here} {username}", Map.of("username", "u1"))).isEqualTo("{typo_here} u1");
    }

    @Test
    void everyTemplateOffersTheCommonVariables() {
        for (EEmailTemplate t : EEmailTemplate.values()) {
            assertThat(t.allVariables()).contains("app_name", "app_url");
        }
        assertThat(EEmailTemplate.USER_CREATE.allVariables()).contains("temporary_password");
        assertThat(EEmailTemplate.PAYOUT_APPROVED.allVariables()).doesNotContain("temporary_password");
    }

    @Test
    void notificationSwitchedOffByTheCompanyIsNotSent() {
        CompanyEmailNotificationRepository toggles = mock(CompanyEmailNotificationRepository.class);
        SettingsService settings = mock(SettingsService.class);
        EmailTemplateService templates = mock(EmailTemplateService.class);
        CompanyEmailNotification off = new CompanyEmailNotification();
        off.setEnabled(false);
        when(toggles.findByCompanyIdAndTemplateCode(10L, "PAYOUT_APPROVED")).thenReturn(Optional.of(off));
        var companyMail = mock(com.terp.erp.iam.settings.CompanyEmailService.class);
        EmailDispatcher d = new EmailDispatcher(templates, toggles, settings, companyMail);

        d.send(new EmailDispatcher.EmailEvent(EEmailTemplate.PAYOUT_APPROVED, 10L, List.of("a@example.invalid"), Map.of()));

        verify(companyMail, never()).mailerFor(any());
        verify(templates, never()).render(anyString(), anyString(), any());
    }

    @Test
    void nothingIsSentWithoutSmtpSettings() {
        SettingsService settings = mock(SettingsService.class);
        EmailTemplateService templates = mock(EmailTemplateService.class);
        var companyMail = mock(com.terp.erp.iam.settings.CompanyEmailService.class);
        when(companyMail.mailerFor(null)).thenReturn(null);
        EmailDispatcher d = new EmailDispatcher(templates, mock(CompanyEmailNotificationRepository.class), settings,
                companyMail);

        d.send(new EmailDispatcher.EmailEvent(EEmailTemplate.COMPANY_CREATE, null, List.of("a@example.invalid"), Map.of()));

        verify(templates, never()).render(anyString(), anyString(), any());
    }
}
