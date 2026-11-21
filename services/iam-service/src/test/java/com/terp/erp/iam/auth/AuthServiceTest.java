package com.terp.erp.iam.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.keycloak.KeycloakTokens;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private static final KeycloakTokens TOKENS = new KeycloakTokens("access", "refresh", 900, 86400, "Bearer");

    private KeycloakClient keycloak;
    private JwtDecoder decoder;
    private UserAccountRepository users;
    private CompanyRepository companies;
    private AuthService service;
    private com.terp.erp.iam.security.RevokedSessionStore revoked;

    @BeforeEach
    void setUp() {
        keycloak = mock(KeycloakClient.class);
        decoder = mock(JwtDecoder.class);
        users = mock(UserAccountRepository.class);
        companies = mock(CompanyRepository.class);
        revoked = new com.terp.erp.iam.security.RevokedSessionStore(java.time.Duration.ofMinutes(15));
        service = new AuthService(keycloak, decoder, users, companies, revoked,
                new com.terp.erp.iam.saas.SubscriptionService(mock(com.terp.erp.iam.repository.PlanRepository.class), users, java.time.Clock.systemUTC()),
                mock(com.terp.erp.iam.ip.IpRestrictionService.class));
        when(keycloak.passwordGrant("demo", "pw")).thenReturn(TOKENS);
    }

    @Test
    void activeUserOfActiveCompanyLogsIn() {
        token("kc-1", List.of());
        account("kc-1", EUserStatus.ACTIVE, ECompanyStatus.ACTIVE);

        assertThat(service.login("demo", "pw").accessToken()).isEqualTo("access");
        verify(keycloak, never()).logout(anyString());
    }

    @Test
    void lockedUserIsRejectedAndSessionEnded() {
        token("kc-1", List.of());
        account("kc-1", EUserStatus.LOCKED, ECompanyStatus.ACTIVE);

        assertError(() -> service.login("demo", "pw"), EIamError.AUTH_002, HttpStatus.UNAUTHORIZED);
        verify(keycloak).logout("refresh");
    }

    @Test
    void userOfSuspendedCompanyIsRejected() {
        token("kc-1", List.of());
        account("kc-1", EUserStatus.ACTIVE, ECompanyStatus.SUSPENDED);

        assertError(() -> service.login("demo", "pw"), EIamError.AUTH_002, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void keycloakUserWithoutErpProfileIsRejected() {
        token("kc-2", List.of());
        when(users.findByKeycloakIdAndDeletedFalse("kc-2")).thenReturn(Optional.empty());

        assertError(() -> service.login("demo", "pw"), EIamError.AUTH_006, HttpStatus.FORBIDDEN);
        verify(keycloak).logout("refresh");
    }

    @Test
    void superAdminNeedsNoCompany() {
        token("kc-root", List.of("SUPER_ADMIN"));
        assertThat(service.login("demo", "pw").refreshToken()).isEqualTo("refresh");
    }

    @Test
    void initialPasswordIsSetOnlyAfterKeycloakConfirmedTheCurrentOne() {
        when(keycloak.passwordGrant("demo", "wrong")).thenThrow(ApiException.unauthorized(EIamError.AUTH_001));

        assertError(() -> service.setInitialPassword("demo", "wrong", "NewPassw0rd!"), EIamError.AUTH_001,
                HttpStatus.UNAUTHORIZED);
        verify(keycloak, never()).setPermanentPassword(anyString(), anyString());
    }

    @Test
    void initialPasswordReplacesTemporaryOneThenLogsIn() {
        when(keycloak.passwordGrant("demo", "temp")).thenThrow(ApiException.badRequest(EIamError.AUTH_004));
        when(keycloak.findUserId("demo")).thenReturn("kc-1");
        when(keycloak.passwordGrant("demo", "NewPassw0rd!")).thenReturn(TOKENS);
        token("kc-1", List.of());
        account("kc-1", EUserStatus.ACTIVE, ECompanyStatus.ACTIVE);

        assertThat(service.setInitialPassword("demo", "temp", "NewPassw0rd!").accessToken()).isEqualTo("access");
        verify(keycloak).setPermanentPassword("kc-1", "NewPassw0rd!");
    }

    @Test
    void logoutRevokesTheSessionOnlyWhenKeycloakAcceptedTheToken() {
        when(keycloak.logout("good")).thenReturn(true);
        when(keycloak.sessionIdOf("good")).thenReturn("sid-1");
        when(keycloak.logout("forged")).thenReturn(false);
        when(keycloak.sessionIdOf("forged")).thenReturn("sid-victim");

        service.logout("forged");
        assertThat(revoked.isRevoked("sid-victim")).isFalse();

        service.logout("good");
        assertThat(revoked.isRevoked("sid-1")).isTrue();
    }

    private void token(String sub, List<String> realmRoles) {
        Jwt jwt = new Jwt("access", Instant.now(), Instant.now().plusSeconds(900), Map.of("alg", "RS256"),
                Map.of("sub", sub, "realm_access", Map.of("roles", realmRoles)));
        when(decoder.decode("access")).thenReturn(jwt);
    }

    private void account(String keycloakId, EUserStatus status, ECompanyStatus companyStatus) {
        UserAccount u = new UserAccount();
        u.setId(1L);
        u.setKeycloakId(keycloakId);
        u.setCompanyId(10L);
        u.setStatus(status);
        when(users.findByKeycloakIdAndDeletedFalse(keycloakId)).thenReturn(Optional.of(u));
        Company c = new Company();
        c.setId(10L);
        c.setStatus(companyStatus);
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(c));
    }

    private static void assertError(Runnable call, EIamError code, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(code);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(status);
                });
    }
}
