package com.terp.erp.iam.user;

import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCreatedEventTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final KeycloakClient keycloak = mock(KeycloakClient.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final UserService service = new UserService(users, roles, keycloak, mock(LocalAuthorityResolver.class),
            mock(com.terp.erp.iam.repository.CompanyRepository.class),
            mock(com.terp.erp.iam.saas.SubscriptionService.class), mock(com.terp.erp.iam.email.EmailNotifier.class),
            outbox, mock(com.terp.erp.iam.saas.PlanSeatEnforcer.class));

    @Test
    @SuppressWarnings("unchecked")
    void aCreatedUserIsPublishedWithoutEmailOrPassword() {
        when(roles.findByCompanyIdAndCodeAndDeletedFalse(5L, EDefaultRole.COMPANY_ADMIN.name()))
                .thenReturn(Optional.of(new Role()));
        when(keycloak.createUser(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("kc-1");
        when(users.save(any(UserAccount.class))).thenAnswer(i -> {
            UserAccount u = i.getArgument(0);
            u.setId(31L);
            return u;
        });

        service.createSelfRegisteredAdmin(5L, "owner@example.invalid", "Test Owner", "Secret#123");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(5L), eq("iam.user.created"), eq("USER"), eq(31L), payload.capture());
        Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p).containsEntry("userId", "31").containsEntry("title", "Test Owner");
        assertThat(p.values()).doesNotContain("owner@example.invalid", "Secret#123");
    }

    @Test
    void aFailedDatabaseWritePublishesNothing() {
        when(roles.findByCompanyIdAndCodeAndDeletedFalse(5L, EDefaultRole.COMPANY_ADMIN.name()))
                .thenReturn(Optional.of(new Role()));
        when(keycloak.createUser(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("kc-1");
        when(users.save(any(UserAccount.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.createSelfRegisteredAdmin(5L, "owner@example.invalid", "Test Owner", "x"))
                .isInstanceOf(IllegalStateException.class);
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }
}
