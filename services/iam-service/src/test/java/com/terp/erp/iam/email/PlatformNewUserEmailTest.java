package com.terp.erp.iam.email;

import com.terp.erp.iam.settings.CompanyEmailService;
import com.terp.erp.iam.settings.SettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformNewUserEmailTest {
    private final SettingsService settings = mock(SettingsService.class);
    private final CompanyEmailService companyMail = mock(CompanyEmailService.class);
    private final EmailDispatcher dispatcher = new EmailDispatcher(mock(EmailTemplateService.class),
            mock(CompanyEmailNotificationRepository.class), settings, companyMail);

    private static EmailDispatcher.EmailEvent event(EEmailTemplate t) {
        return new EmailDispatcher.EmailEvent(t, 10L, List.of("new.user@example.invalid"), Map.of());
    }

    @Test
    void newUserEmailSwitchedOffByThePlatformIsNotSent() {
        when(settings.platformEmailDisabled(EPlatformEmail.NEW_USER)).thenReturn(true);

        dispatcher.send(event(EEmailTemplate.USER_CREATE));

        verify(companyMail, never()).mailerFor(any());
    }

    @Test
    void newUserEmailGoesOnWhenThePlatformSwitchIsOn() {
        when(settings.platformEmailDisabled(EPlatformEmail.NEW_USER)).thenReturn(false);

        dispatcher.send(event(EEmailTemplate.USER_CREATE));

        verify(companyMail).mailerFor(10L);
    }

    @Test
    void theNewUserSwitchDoesNotTouchOtherEmails() {
        when(settings.platformEmailDisabled(EPlatformEmail.NEW_USER)).thenReturn(true);

        dispatcher.send(event(EEmailTemplate.PASSWORD_RESET));

        verify(companyMail).mailerFor(10L);
    }
}
