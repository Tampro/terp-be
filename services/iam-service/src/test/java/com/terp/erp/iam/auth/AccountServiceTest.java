package com.terp.erp.iam.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.company.CompanyService;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.email.EmailNotifier;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.keycloak.KeycloakTokens;
import com.terp.erp.iam.me.AvatarMediaClient;
import com.terp.erp.iam.me.ProfileService;
import com.terp.erp.iam.model.AccountToken;
import com.terp.erp.iam.model.AccountToken.EPurpose;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.AccountTokenRepository;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.settings.SettingsService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final AccountTokenRepository tokens = mock(AccountTokenRepository.class);
    private final KeycloakClient keycloak = mock(KeycloakClient.class);
    private final EmailNotifier notifier = mock(EmailNotifier.class);
    private final SettingsService settings = mock(SettingsService.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AccountService service = new AccountService(users, companies, tokens, keycloak, notifier, settings,
            mock(CompanyService.class), clock);
    private Company company;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setStatus(ECompanyStatus.ACTIVE);
        user = new UserAccount();
        user.setId(1L);
        user.setCompanyId(10L);
        user.setKeycloakId("kc-1");
        user.setUsername("a.test");
        user.setEmail("a@example.invalid");
        user.setStatus(EUserStatus.ACTIVE);
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(company));
        when(users.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forgotPasswordStoresOnlyTheHashAndMailsTheLink() {
        UserAccount locked = new UserAccount();
        locked.setId(2L);
        locked.setCompanyId(10L);
        locked.setStatus(EUserStatus.LOCKED);
        when(users.findByEmailIgnoreCaseAndDeletedFalse("a@example.invalid")).thenReturn(List.of(user, locked));

        service.forgotPassword(" a@example.invalid ");

        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        verify(tokens).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notifier).passwordReset(eq(user), eq(company), link.capture(), eq(30L));
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(AccountTokens.hash(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
        // the locked account gets nothing
        verify(notifier, never()).passwordReset(eq(locked), any(), anyString(), anyLong());
    }

    @Test
    void resetPasswordSpendsTheLinkAndEndsSessions() {
        AccountToken t = token(EPurpose.PASSWORD_RESET, NOW.plusSeconds(60), null);
        when(tokens.findByTokenHash(AccountTokens.hash("tok"))).thenReturn(Optional.of(t));

        service.resetPassword("tok", "New-Pass-123");

        verify(keycloak).setPermanentPassword("kc-1", "New-Pass-123");
        verify(keycloak).logoutSessions("kc-1");
        assertThat(t.getUsedAt()).isEqualTo(NOW);
        // a second use is refused
        assertThatThrownBy(() -> service.resetPassword("tok", "Other-Pass-123"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.AUTH_014);
    }

    @Test
    void expiredOrWrongPurposeLinkIsRefused() {
        when(tokens.findByTokenHash(AccountTokens.hash("old")))
                .thenReturn(Optional.of(token(EPurpose.PASSWORD_RESET, NOW.minusSeconds(1), null)));
        when(tokens.findByTokenHash(AccountTokens.hash("verify")))
                .thenReturn(Optional.of(token(EPurpose.EMAIL_VERIFY, NOW.plusSeconds(60), null)));
        assertThatThrownBy(() -> service.resetPassword("old", "New-Pass-123"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.AUTH_014);
        assertThatThrownBy(() -> service.resetPassword("verify", "New-Pass-123"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.AUTH_014);
        assertThatThrownBy(() -> service.resetPassword("unknown", "New-Pass-123"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.AUTH_014);
        verify(keycloak, never()).setPermanentPassword(anyString(), anyString());
    }

    @Test
    void registrationNeedsTheSwitch() {
        when(settings.system().userRegistration()).thenReturn(false);
        assertThatThrownBy(() -> service.register("A Test", "a@example.invalid", "Secret-123", null, null))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.AUTH_015);
    }

    @Test
    void verifyEmailLetsTheCompanyLogIn() {
        EmailVerificationService v = new EmailVerificationService(tokens, companies, notifier, settings, clock);
        company.setEmailVerifyRequired(true);
        AccountToken t = token(EPurpose.EMAIL_VERIFY, NOW.plusSeconds(60), 10L);
        when(tokens.findByTokenHash(AccountTokens.hash("v"))).thenReturn(Optional.of(t));
        v.verify("v");
        assertThat(company.isEmailVerifyRequired()).isFalse();
        assertThat(company.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(t.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void changePasswordWithAWrongCurrentPasswordIsAuth017() {
        com.terp.erp.iam.user.UserProfileDeleteTest.login(1L, 10L);
        ProfileService profiles = new ProfileService(users, keycloak, mock(AvatarMediaClient.class));
        doThrow(ApiException.unauthorized(EIamError.AUTH_001)).when(keycloak).passwordGrant("user1", "wrong");
        assertThatThrownBy(() -> profiles.changePassword(new ProfileService.PasswordForm("wrong", "New-Pass-123",
                "New-Pass-123"))).extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.AUTH_017);
        verify(keycloak, never()).setPermanentPassword(anyString(), anyString());

        when(keycloak.passwordGrant("user1", "right")).thenReturn(new KeycloakTokens("a", "r", 60, 60, "Bearer"));
        profiles.changePassword(new ProfileService.PasswordForm("right", "New-Pass-123", "New-Pass-123"));
        verify(keycloak).logout("r");
        verify(keycloak).setPermanentPassword("kc-1", "New-Pass-123");
        verify(keycloak).logoutSessions("kc-1");
    }

    private AccountToken token(EPurpose purpose, Instant expires, Long companyId) {
        AccountToken t = new AccountToken();
        t.setPurpose(purpose);
        t.setUserId(1L);
        t.setCompanyId(companyId);
        t.setExpiresAt(expires);
        return t;
    }
}
