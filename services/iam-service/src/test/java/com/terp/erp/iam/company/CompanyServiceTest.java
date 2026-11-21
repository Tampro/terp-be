package com.terp.erp.iam.company;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.role.RoleService;
import com.terp.erp.iam.saas.OrderService;
import com.terp.erp.iam.saas.SubscriptionService;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.iam.user.UserService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

class CompanyServiceTest {
    private CompanyRepository companies;
    private UserAccountRepository users;
    private KeycloakClient keycloak;
    private OrderService orders;
    private CompanyService service;
    private Company company;
    private final com.terp.erp.iam.auth.EmailVerificationService verification =
            mock(com.terp.erp.iam.auth.EmailVerificationService.class);
    private final UserService userService = mock(UserService.class);

    @BeforeEach
    void setUp() {
        companies = mock(CompanyRepository.class);
        users = mock(UserAccountRepository.class);
        keycloak = mock(KeycloakClient.class);
        orders = mock(OrderService.class);
        service = new CompanyService(companies, mock(RoleRepository.class), mock(PlanRepository.class), users,
                mock(RoleService.class), userService, orders, mock(SubscriptionService.class), keycloak,
                mock(LocalAuthorityResolver.class), mock(com.terp.erp.iam.referral.ReferralService.class), verification);
        company = new Company();
        company.setId(10L);
        company.setCode("ACME");
        company.setStatus(ECompanyStatus.ACTIVE);
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(company));
    }

    @Test
    void resetPasswordDefaultsToTheFirstActiveCompanyAdminAndEndsItsSessions() {
        UserAccount locked = user(1L, 10L, "kc-1", EUserStatus.LOCKED);
        UserAccount active = user(2L, 10L, "kc-2", EUserStatus.ACTIVE);
        when(users.findByCompanyAndRole(10L, "COMPANY_ADMIN")).thenReturn(List.of(locked, active));

        assertThat(service.resetPassword(10L, null, "N3w-Passw0rd!").id()).isEqualTo("2");
        verify(keycloak).setPermanentPassword("kc-2", "N3w-Passw0rd!");
        verify(keycloak).logoutSessions("kc-2");
    }

    @Test
    void resetPasswordRefusesAUserOfAnotherCompany() {
        when(users.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(user(5L, 99L, "kc-5", EUserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.resetPassword(10L, 5L, "N3w-Passw0rd!"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_009);
        verify(keycloak, never()).setPermanentPassword(anyString(), anyString());
    }

    @Test
    void resetPasswordWithoutActiveAdminIsReported() {
        when(users.findByCompanyAndRole(10L, "COMPANY_ADMIN")).thenReturn(List.of());
        assertThatThrownBy(() -> service.resetPassword(10L, null, "N3w-Passw0rd!"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_016);
    }

    @Test
    void deleteIsSoftRemovesEveryAccountFromKeycloakAndCancelsOpenOrders() {
        UserAccount active = user(1L, 10L, "kc-1", EUserStatus.ACTIVE);
        UserAccount locked = user(2L, 10L, "kc-2", EUserStatus.LOCKED);
        when(users.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(active, locked));

        service.delete(10L);

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.getStatus()).isEqualTo(ECompanyStatus.SUSPENDED);
        // Removed, not just disabled: the usernames become free for another company.
        verify(keycloak).deleteUser("kc-1");
        verify(keycloak).deleteUser("kc-2");
        verify(keycloak, never()).setEnabled(anyString(), anyBoolean());
        assertThat(active.isDeleted()).isTrue();
        assertThat(active.getStatus()).isEqualTo(EUserStatus.LOCKED);
        assertThat(locked.isDeleted()).isTrue();
        verify(orders).cancelPendingOf(10L);
    }

    @Test
    void selfRegisteredCompanyWaitsForEmailVerificationWhenItIsOn() {
        when(verification.enabled()).thenReturn(true);
        when(companies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> {
            Company c = i.getArgument(0);
            c.setId(20L);
            return c;
        });
        Company c = service.register("Công ty Đông Á", "owner@example.invalid", "Owner Test", "Secret-123", null);
        assertThat(c.getCode()).matches("CONGTYDONGA_[A-Z0-9]{4}");
        assertThat(c.isEmailVerifyRequired()).isTrue();
        assertThat(c.getEmailVerifiedAt()).isNull();
        verify(userService).createSelfRegisteredAdmin(20L, "owner@example.invalid", "Owner Test", "Secret-123");
        verify(verification).issue(c);

        when(verification.enabled()).thenReturn(false);
        Company d = service.register("Beta", "b@example.invalid", "B Test", "Secret-123", null);
        assertThat(d.isEmailVerifyRequired()).isFalse();
        assertThat(d.getEmailVerifiedAt()).isNotNull();
    }

    private static UserAccount user(Long id, Long companyId, String kc, EUserStatus status) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setCompanyId(companyId);
        u.setKeycloakId(kc);
        u.setUsername("user" + id);
        u.setStatus(status);
        return u;
    }
}
