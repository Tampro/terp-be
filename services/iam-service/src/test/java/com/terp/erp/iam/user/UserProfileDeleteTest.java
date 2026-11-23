package com.terp.erp.iam.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.login.LoginHistoryService;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.iam.user.UserDtos.UserProfileForm;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserProfileDeleteTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final KeycloakClient keycloak = mock(KeycloakClient.class);
    private final UserService service = new UserService(users, mock(RoleRepository.class), keycloak,
            mock(LocalAuthorityResolver.class), mock(com.terp.erp.iam.repository.CompanyRepository.class),
            mock(com.terp.erp.iam.saas.SubscriptionService.class), mock(com.terp.erp.iam.email.EmailNotifier.class),
            mock(com.terp.erp.outbox.OutboxPublisher.class));
    private final UserAccount admin = account(7L, true);

    @BeforeEach
    void setUp() {
        login(1L, 10L, "IAM_USER_UPDATE");
        when(users.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void editSyncsKeycloakThenTheRow() {
        var dto = service.updateProfile(7L, new UserProfileForm(" Test Seven ", " seven@example.invalid "));
        verify(keycloak).updateProfile("kc-7", "seven@example.invalid", "Test Seven");
        assertThat(dto.fullName()).isEqualTo("Test Seven");
        assertThat(dto.email()).isEqualTo("seven@example.invalid");
        login(7L, 10L, "IAM_USER_UPDATE");
        assertCode(() -> service.updateProfile(7L, new UserProfileForm("X", "x@example.invalid")), EIamError.IAM_013);
    }

    @Test
    void deleteRemovesFromKeycloakButKeepsTheLastAdmin() {
        when(users.findByCompanyAndRole(10L, EDefaultRole.COMPANY_ADMIN.name())).thenReturn(List.of(admin));
        assertCode(() -> service.delete(7L), EIamError.IAM_018);
        verify(keycloak, never()).deleteUser("kc-7");
        when(users.findByCompanyAndRole(10L, EDefaultRole.COMPANY_ADMIN.name()))
                .thenReturn(List.of(admin, account(8L, true)));
        service.delete(7L);
        assertThat(admin.isDeleted()).isTrue();
        assertThat(admin.getStatus()).isEqualTo(EUserStatus.LOCKED);
        verify(keycloak).deleteUser("kc-7");
    }

    @Test
    void loginHistoryHelpers() {
        assertThat(LoginHistoryService.clientIp(" 203.0.113.9 ", "172.18.0.4")).isEqualTo("203.0.113.9");
        assertThat(LoginHistoryService.clientIp(null, "172.18.0.4")).isEqualTo("172.18.0.4");
        String chrome = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/131.0 Safari/537.36";
        assertThat(LoginHistoryService.browser(chrome)).isEqualTo("Chrome");
        assertThat(LoginHistoryService.os(chrome)).isEqualTo("Windows");
        String edge = chrome + " Edg/131.0";
        assertThat(LoginHistoryService.browser(edge)).isEqualTo("Edge");
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1";
        assertThat(LoginHistoryService.os(iphone)).isEqualTo("iOS");
        assertThat(LoginHistoryService.browser(iphone)).isEqualTo("Safari");
    }

    private static UserAccount account(Long id, boolean admin) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setKeycloakId("kc-" + id);
        u.setCompanyId(10L);
        u.setUsername("user" + id);
        u.setStatus(EUserStatus.ACTIVE);
        if (admin) {
            Role r = new Role();
            r.setCode(EDefaultRole.COMPANY_ADMIN.name());
            u.getRoles().add(r);
        }
        return u;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EIamError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }

    public static void login(Long userId, Long companyId, String... perms) {
        ErpPrincipal p = new ErpPrincipal(userId, "kc-" + userId, "user" + userId, companyId, false, Set.of(),
                Set.of(perms));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
