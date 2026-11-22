package com.terp.erp.iam.email;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.email.ENotificationEvent.ENotificationModule;
import com.terp.erp.iam.user.UserProfileDeleteTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTest {
    private final CompanyEmailNotificationRepository toggles = mock(CompanyEmailNotificationRepository.class);
    private final EmailTemplateRepository templates = mock(EmailTemplateRepository.class);
    private final EmailNotificationService service = new EmailNotificationService(toggles, templates);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CompanyEmailNotification off(String code) {
        CompanyEmailNotification n = new CompanyEmailNotification();
        n.setCompanyId(10L);
        n.setTemplateCode(code);
        n.setEnabled(false);
        return n;
    }

    @Test
    void listIsGroupedByModuleWithSwitchState() {
        UserProfileDeleteTest.login(1L, 10L, "NOTIFICATION_SETTINGS_UPDATE");
        when(toggles.findByCompanyId(10L)).thenReturn(List.of(off("DEAL_MOVED"), off("PLAN_ORDER_APPROVED")));
        when(templates.findAll()).thenReturn(List.of());

        var groups = service.list(null);

        assertThat(groups).extracting(EmailNotificationService.NotificationGroup::module)
                .containsExactly(ENotificationModule.GENERAL, ENotificationModule.CRM, ENotificationModule.SUPPORT,
                        ENotificationModule.RECRUITMENT, ENotificationModule.SUBSCRIPTION);
        var general = groups.get(0).notifications();
        assertThat(general.get(0).code()).isEqualTo("USER_CREATE");
        assertThat(general.get(0).dispatched()).isTrue();
        assertThat(general).extracting(EmailNotificationService.NotificationDto::code)
                .contains("INVOICE_SENT", "PAYMENT_REMINDER", "INVOICE_PAYMENT_CREATED", "PROPOSAL_STATUS_UPDATED",
                        "HELPDESK_TICKET_CREATED", "HELPDESK_TICKET_REPLY", "PURCHASE_SENT", "PURCHASE_PAYMENT_CREATED");
        var crm = groups.get(1).notifications();
        assertThat(crm).filteredOn(n -> n.code().equals("DEAL_MOVED")).singleElement()
                .satisfies(n -> {
                    assertThat(n.enabled()).isFalse();
                    assertThat(n.dispatched()).isFalse();
                });
        assertThat(crm).filteredOn(n -> n.code().equals("LEAD_ASSIGNED")).singleElement()
                .satisfies(n -> assertThat(n.enabled()).isTrue());
        assertThat(groups.get(4).notifications()).filteredOn(n -> n.code().equals("PLAN_ORDER_APPROVED"))
                .singleElement().satisfies(n -> assertThat(n.enabled()).isFalse());
        // Account emails cannot be switched off and are not listed.
        assertThat(groups).flatMap(EmailNotificationService.NotificationGroup::notifications)
                .extracting(EmailNotificationService.NotificationDto::code)
                .doesNotContain("PASSWORD_RESET", "EMAIL_VERIFY", "COMPANY_CREATE");
    }

    @Test
    void toggleStoresTheEventForTheCallersCompany() {
        UserProfileDeleteTest.login(1L, 10L, "NOTIFICATION_SETTINGS_UPDATE");
        when(toggles.findByCompanyIdAndTemplateCode(10L, "SUPPORT_TICKET_CREATED")).thenReturn(Optional.empty());

        var dto = service.toggle("SUPPORT_TICKET_CREATED", false);

        ArgumentCaptor<CompanyEmailNotification> saved = ArgumentCaptor.forClass(CompanyEmailNotification.class);
        verify(toggles).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isEqualTo(10L);
        assertThat(saved.getValue().isEnabled()).isFalse();
        assertThat(dto.name()).isEqualTo("New Ticket");

        when(toggles.findByCompanyIdAndTemplateCode(10L, "SUPPORT_TICKET_CREATED"))
                .thenReturn(Optional.of(saved.getValue()));
        assertThat(service.enabled(null, "SUPPORT_TICKET_CREATED").enabled()).isFalse();
        when(toggles.findByCompanyIdAndTemplateCode(10L, "LEAD_MOVED")).thenReturn(Optional.empty());
        assertThat(service.enabled(null, "LEAD_MOVED").enabled()).isTrue();
    }

    @Test
    void unknownAndLockedCodesAreRefused() {
        UserProfileDeleteTest.login(1L, 10L, "NOTIFICATION_SETTINGS_UPDATE");
        assertThatThrownBy(() -> service.toggle("NOPE", true)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(EEmailError.EMAIL_001);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
        assertThatThrownBy(() -> service.toggle("PASSWORD_RESET", false)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(EEmailError.EMAIL_003));
        verify(toggles, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void catalogCodesNeverCollideWithTemplates() {
        for (ENotificationEvent e : ENotificationEvent.values()) {
            assertThat(java.util.Arrays.stream(EEmailTemplate.values()).map(Enum::name)).doesNotContain(e.name());
            assertThat(e.name().length()).isLessThanOrEqualTo(50);
        }
    }
}
