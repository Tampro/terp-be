package com.terp.erp.iam.template;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.email.EEmailError;
import com.terp.erp.iam.integration.CompanyIntegrationService;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.template.CompanyNotificationTemplate.ETemplateChannel;
import com.terp.erp.iam.template.NotificationTemplateService.ChatPreviewForm;
import com.terp.erp.iam.template.NotificationTemplateService.ChatTemplateForm;
import com.terp.erp.iam.template.NotificationTemplateService.EmailPreviewForm;
import com.terp.erp.iam.template.NotificationTemplateService.EmailTemplateForm;
import com.terp.erp.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTemplateServiceTest {
    private final CompanyNotificationTemplateRepository repo = mock(CompanyNotificationTemplateRepository.class);
    private final CompanyIntegrationService integrations = mock(CompanyIntegrationService.class);
    private final NotificationTemplateService service = new NotificationTemplateService(repo, integrations);

    @BeforeEach
    void defaults() {
        when(repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repo.findByCompanyIdAndChannelAndTemplateKey(any(), any(), any())).thenReturn(List.of());
        when(repo.findByCompanyIdAndChannel(any(), any())).thenReturn(List.of());
        when(integrations.slackWebhookUrl(any())).thenReturn(Optional.empty());
        when(integrations.telegramTarget(any())).thenReturn(Optional.empty());
        when(integrations.twilioTarget(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anEmailOverrideIsSavedSanitisedForTheCallersOwnCompany() {
        companyUser(10L);
        ArgumentCaptor<CompanyNotificationTemplate> saved = ArgumentCaptor.forClass(CompanyNotificationTemplate.class);

        service.emailSave(20L, "SUPPORT_TICKET_CREATED", "en", new EmailTemplateForm("Ticket {ticketNumber}\r\nBcc: x",
                "<p onclick=\"x()\">Hello {name}</p><script>alert(1)</script>"));

        verify(repo).save(saved.capture());
        CompanyNotificationTemplate t = saved.getValue();
        assertThat(t.getCompanyId()).isEqualTo(10L);
        assertThat(t.getChannel()).isEqualTo(ETemplateChannel.EMAIL);
        assertThat(t.getSubject()).isEqualTo("Ticket {ticketNumber} Bcc: x");
        assertThat(t.getBody()).isEqualTo("<p>Hello {name}</p>");
    }

    @Test
    void anUnknownPlaceholderOrCodeOrLanguageIsRefused() {
        companyUser(10L);
        assertThatThrownBy(() -> service.emailSave(null, "SUPPORT_TICKET_CREATED", "en",
                new EmailTemplateForm("Hi {password}", "<p>x</p>")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_025);
        assertThatThrownBy(() -> service.emailGet(null, "NOPE"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EEmailError.EMAIL_001);
        assertThatThrownBy(() -> service.emailSave(null, "SUPPORT_TICKET_CREATED", "fr",
                new EmailTemplateForm("Hi", "<p>x</p>")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EEmailError.EMAIL_002);
        verify(repo, never()).save(any());
    }

    @Test
    void resetDeletesTheOverrideOfThatLanguageOnly() {
        companyUser(10L);
        CompanyNotificationTemplate t = row(10L, ETemplateChannel.EMAIL, "PAYMENT_REMINDER", "vi", "S", "<p>B</p>");
        when(repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(10L, ETemplateChannel.EMAIL, "PAYMENT_REMINDER",
                "vi")).thenReturn(Optional.of(t));

        var v = service.emailReset(null, "PAYMENT_REMINDER", "vi");

        verify(repo).delete(t);
        assertThat(v.languages()).allSatisfy(l -> assertThat(l.customised()).isFalse());
    }

    @Test
    void theListFlagsCustomisedTemplatesAndDocumentsVariables() {
        companyUser(10L);
        when(repo.findByCompanyIdAndChannel(10L, ETemplateChannel.EMAIL)).thenReturn(List.of(
                row(10L, ETemplateChannel.EMAIL, "INVOICE_SENT", "en", "S", "B"),
                // a row of another company (never returned by the query, but must never count)
                row(99L, ETemplateChannel.EMAIL, "PAYMENT_REMINDER", "en", "S", "B")));

        var rows = service.emailList(null).stream().flatMap(g -> g.templates().stream()).toList();

        assertThat(rows).filteredOn(r -> r.code().equals("INVOICE_SENT")).singleElement()
                .satisfies(r -> {
                    assertThat(r.customised()).isTrue();
                    assertThat(r.customisedLanguages()).containsExactly("en");
                    assertThat(r.variables()).containsExactly("number", "customerName", "total", "dueDate");
                });
        assertThat(rows).filteredOn(r -> r.code().equals("PAYMENT_REMINDER")).singleElement()
                .satisfies(r -> assertThat(r.customised()).isFalse());
    }

    @Test
    void emailPreviewEscapesSampleValuesAndSanitisesTheRequestBody() {
        companyUser(10L);
        var p = service.emailPreview(null, "SUPPORT_TICKET_CREATED", "en",
                new EmailPreviewForm("Ticket {ticketNumber}", "<p>{name}{unknown}</p><img src=x onerror=y>"));
        assertThat(p.subject()).isEqualTo("Ticket [ticketNumber]");
        assertThat(p.body()).isEqualTo("<p>[name]</p><img>").doesNotContain("onerror");

        assertThatThrownBy(() -> service.emailPreview(null, "SUPPORT_TICKET_CREATED", "en", null))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_023);
    }

    @Test
    void htmlValuesAreEscapedAndUnknownKeysAreEmpty() {
        assertThat(TemplateVariables.fill("<p>{name}{x}</p>", Map.of("name", "<script>"), true))
                .isEqualTo("<p>&lt;script&gt;</p>");
        assertThat(TemplateVariables.fill("A {name}", Map.of("name", "x\nTo: y"), false)).isEqualTo("A x To: y");
    }

    @Test
    void aChatTemplateCannotBeSavedBeforeTheIntegrationIsConfigured() {
        companyUser(10L);
        var list = service.chatList(null, "slack");
        assertThat(list.configured()).isFalse();
        assertThat(list.modules()).isNotEmpty();

        assertThatThrownBy(() -> service.chatSave(null, "slack", "support.ticket.created", "en",
                new ChatTemplateForm("New ticket {ticketNumber}")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_022);
        verify(repo, never()).save(any());
    }

    @Test
    void aConfiguredChannelSavesTheTextWithinItsLimit() {
        companyUser(10L);
        when(integrations.twilioTarget(10L)).thenReturn(Optional.of(
                new CompanyIntegrationService.TwilioTarget("AC1", "t", "+10000000000")));
        when(integrations.slackWebhookUrl(10L)).thenReturn(Optional.of("https://hooks.slack.com/services/x"));

        service.chatSave(null, "sms", "support.ticket.created", "vi", new ChatTemplateForm("x".repeat(1600)));
        assertThatThrownBy(() -> service.chatSave(null, "slack", "support.ticket.created", "vi",
                new ChatTemplateForm("x".repeat(1001))))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_024);
        // the private parts of a lead email never reach a chat channel
        assertThatThrownBy(() -> service.chatSave(null, "slack", "crm.lead.emailed", "vi",
                new ChatTemplateForm("{body}")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_025);
        assertThatThrownBy(() -> service.chatGet(null, "slack", "no.such.event"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_021);
        assertThatThrownBy(() -> service.chatGet(null, "email", "support.ticket.created"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ESettingsError.SETTINGS_020);
    }

    @Test
    void chatPreviewRendersSamplesOnOneLinePerValue() {
        companyUser(10L);
        var p = service.chatPreview(null, "telegram", "support.ticket.created", "en",
                new ChatPreviewForm("Ticket {ticketNumber}: {subject} {nothing}"));
        assertThat(p.text()).isEqualTo("Ticket [ticketNumber]: [subject]");
    }

    @Test
    void overridesAreOfTheRequestedCompanyAndLanguageOnly() {
        when(repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(7L, ETemplateChannel.EMAIL, "SUPPORT_TICKET_CREATED",
                "en")).thenReturn(Optional.of(row(7L, ETemplateChannel.EMAIL, "SUPPORT_TICKET_CREATED", "en", "S",
                "<p>B</p>")));
        when(repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(7L, ETemplateChannel.SMS, "support.ticket.created",
                "en")).thenReturn(Optional.of(row(7L, ETemplateChannel.SMS, "support.ticket.created", "en", null,
                "SMS {ticketNumber}")));

        var o = service.overrides(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", "en-US");
        assertThat(o.email()).isEqualTo(new NotificationTemplateService.EmailOverride("S", "<p>B</p>"));
        assertThat(o.sms()).isEqualTo("SMS {ticketNumber}");
        assertThat(o.slack()).isNull();

        var other = service.overrides(8L, "support.ticket.created", "SUPPORT_TICKET_CREATED", "en");
        assertThat(other.email()).isNull();
        assertThat(other.sms()).isNull();
        assertThat(service.overrides(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", "vi").email()).isNull();
        assertThat(service.overrides(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", "fr").email()).isNull();
    }

    @Test
    void everyHandlerHasAPermission() {
        for (Class<?> c : List.of(EmailNotificationTemplateController.class, ChatNotificationTemplateController.class)) {
            List<Method> handlers = Arrays.stream(c.getDeclaredMethods())
                    .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).toList();
            assertThat(handlers).hasSize(5).allSatisfy(m -> assertThat(m.getAnnotation(PreAuthorize.class))
                    .as(c.getSimpleName() + "." + m.getName()).isNotNull());
        }
        assertThat(Arrays.stream(EmailNotificationTemplateController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(PreAuthorize.class)).filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value))
                .allMatch(v -> v.equals("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')"));
        assertThat(Arrays.stream(ChatNotificationTemplateController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(PreAuthorize.class)).filter(java.util.Objects::nonNull)
                .map(PreAuthorize::value))
                .allMatch(v -> v.equals("@PermissionChecker.has('IAM_SETTINGS_MANAGE')"));
    }

    private static CompanyNotificationTemplate row(Long cid, ETemplateChannel ch, String key, String lang,
                                                   String subject, String body) {
        CompanyNotificationTemplate t = new CompanyNotificationTemplate();
        t.setCompanyId(cid);
        t.setChannel(ch);
        t.setTemplateKey(key);
        t.setLang(lang);
        t.setSubject(subject);
        t.setBody(body);
        return t;
    }

    private static void companyUser(Long companyId) {
        ErpPrincipal p = new ErpPrincipal(7L, "kc-7", "user7", companyId, false, Set.of(),
                Set.of("NOTIFICATION_SETTINGS_UPDATE", "IAM_SETTINGS_MANAGE"));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
