package com.terp.erp.iam.integration;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.integration.ChannelEventService.ChannelEventsForm;
import com.terp.erp.iam.integration.CompanyChannelEvent.EChatChannel;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelEventServiceTest {
    private final CompanyChannelEventRepository repo = mock(CompanyChannelEventRepository.class);
    private final ChannelEventService service = new ChannelEventService(repo);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aCompanyThatNeverSavedHasEveryEventOn() {
        companyUser(10L);

        var v = service.get(null, "slack");

        assertThat(v.configured()).isFalse();
        assertThat(v.modules()).flatExtracting(ChannelEventService.ModuleSwitches::events)
                .hasSize(EChannelEvent.values().length)
                .allSatisfy(e -> assertThat(e.enabled()).isTrue());
        assertThat(service.enabled(10L, EChatChannel.SLACK, "crm.lead.created")).isTrue();
    }

    @Test
    void aCompanyUserAlwaysWritesItsOwnCompanyWhateverItAsksFor() {
        companyUser(10L);

        service.replace(20L, "telegram", new ChannelEventsForm(List.of("crm.lead.created")));

        ArgumentCaptor<CompanyChannelEvent> rows = ArgumentCaptor.forClass(CompanyChannelEvent.class);
        verify(repo, atLeastOnce()).save(rows.capture());
        assertThat(rows.getAllValues()).hasSize(EChannelEvent.values().length)
                .allSatisfy(r -> {
                    assertThat(r.getCompanyId()).isEqualTo(10L);
                    assertThat(r.getChannel()).isEqualTo(EChatChannel.TELEGRAM);
                    assertThat(r.isEnabled()).isEqualTo(r.getEventType().equals("crm.lead.created"));
                });
    }

    @Test
    void aSwitchedOffEventIsReportedOffToNotification() {
        CompanyChannelEvent off = new CompanyChannelEvent();
        off.setEnabled(false);
        when(repo.findByCompanyIdAndChannelAndEventType(10L, EChatChannel.SMS, "sales.invoice.posted"))
                .thenReturn(Optional.of(off));

        assertThat(service.enabled(10L, EChatChannel.SMS, "sales.invoice.posted")).isFalse();
        assertThat(service.enabled(10L, EChatChannel.SMS, null)).isTrue();
    }

    @Test
    void unknownChannelOrEventIsRefusedAndASuperAdminMustNameTheCompany() {
        companyUser(10L);
        assertThatThrownBy(() -> service.get(null, "whatsapp")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_020));
        assertThatThrownBy(() -> service.replace(null, "sms", new ChannelEventsForm(List.of("x.y.z"))))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_021));
        verify(repo, never()).save(any());

        login(new ErpPrincipal(null, "kc-root", "superadmin", null, true, Set.of(ErpPrincipal.SUPER_ADMIN_ROLE),
                Set.of()));
        assertThatThrownBy(() -> service.get(null, "slack")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(EIamError.IAM_003));
    }

    @Test
    void everyEndpointNeedsSettingsManage() {
        Method[] handlers = Arrays.stream(ChannelEventController.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())).toArray(Method[]::new);
        assertThat(handlers).hasSize(2).allSatisfy(m -> assertThat(m.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@PermissionChecker.has('IAM_SETTINGS_MANAGE')"));
    }

    private static void companyUser(Long companyId) {
        login(new ErpPrincipal(7L, "kc-7", "user7", companyId, false, Set.of(), Set.of("IAM_SETTINGS_MANAGE")));
    }

    private static void login(ErpPrincipal p) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
