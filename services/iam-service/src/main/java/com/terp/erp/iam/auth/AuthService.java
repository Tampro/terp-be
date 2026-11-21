package com.terp.erp.iam.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.auth.AuthForms.TokenDto;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.keycloak.KeycloakTokens;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.saas.SubscriptionService;
import com.terp.erp.iam.security.RevokedSessionStore;
import com.terp.erp.security.ErpJwtAuthenticationConverter;

import java.util.Optional;

/**
 * Login facade over Keycloak, so the FE keeps its {@code POST /auth/login {username, password}} contract
 * (docs/analysis/04 §4). Keycloak checks the password, brute-force lockout and sessions; this service adds the ERP
 * rules: the account must exist here, be ACTIVE, and belong to an ACTIVE company.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final KeycloakClient keycloak;
    private final JwtDecoder jwtDecoder;
    private final UserAccountRepository users;
    private final CompanyRepository companies;
    private final RevokedSessionStore revokedSessions;
    private final SubscriptionService subscriptions;
    private final com.terp.erp.iam.ip.IpRestrictionService ipRestriction;

    @Transactional(readOnly = true)
    public TokenDto login(String username, String password) {
        return login(username, password, null);
    }

    /** clientIp = X-Client-IP of the gateway; checked against the company's IP restriction (AUTH_012). */
    @Transactional(readOnly = true)
    public TokenDto login(String username, String password, String clientIp) {
        KeycloakTokens tokens = keycloak.passwordGrant(username, password);
        assertMayUseErp(tokens, clientIp);
        return toDto(tokens);
    }

    /** Keycloak rotates the refresh token (revokeRefreshToken in the realm), so the old one stops working. */
    @Transactional(readOnly = true)
    public TokenDto refresh(String refreshToken) {
        return refresh(refreshToken, null);
    }

    @Transactional(readOnly = true)
    public TokenDto refresh(String refreshToken, String clientIp) {
        KeycloakTokens tokens = keycloak.refresh(refreshToken);
        assertMayUseErp(tokens, clientIp);
        return toDto(tokens);
    }

    /**
     * Ends the Keycloak session and revokes its access tokens at once. The sid is revoked only after Keycloak
     * accepted the refresh token — otherwise anyone could send a forged token carrying another user's sid and
     * log them out.
     */
    public void logout(String refreshToken) {
        if (keycloak.logout(refreshToken)) {
            revokedSessions.revoke(keycloak.sessionIdOf(refreshToken));
        }
    }

    /**
     * First login with an administrator-given temporary password. Keycloak refuses the password grant with
     * "not fully set up" only after the password matched, so that answer is the proof of the current password;
     * any other outcome is reported as is and nothing is changed.
     */
    @Transactional(readOnly = true)
    public TokenDto setInitialPassword(String username, String currentPassword, String newPassword) {
        return setInitialPassword(username, currentPassword, newPassword, null);
    }

    @Transactional(readOnly = true)
    public TokenDto setInitialPassword(String username, String currentPassword, String newPassword, String clientIp) {
        try {
            KeycloakTokens tokens = keycloak.passwordGrant(username, currentPassword);
            // No pending action: the password is already permanent — just log in.
            assertMayUseErp(tokens, clientIp);
            return toDto(tokens);
        } catch (ApiException e) {
            if (e.getErrorCode() != EIamError.AUTH_004) {
                throw e;
            }
        }
        String keycloakId = keycloak.findUserId(username);
        if (keycloakId == null) {
            throw ApiException.unauthorized(EIamError.AUTH_001);
        }
        keycloak.setPermanentPassword(keycloakId, newPassword);
        return login(username, newPassword, clientIp);
    }

    private void assertMayUseErp(KeycloakTokens tokens, String clientIp) {
        Jwt jwt = jwtDecoder.decode(tokens.accessToken());
        if (ErpJwtAuthenticationConverter.isSuperAdmin(jwt)) {
            return;
        }
        Optional<UserAccount> account = users.findByKeycloakIdAndDeletedFalse(jwt.getSubject());
        if (account.isEmpty()) {
            keycloak.logout(tokens.refreshToken());
            throw ApiException.forbidden(EIamError.AUTH_006);
        }
        UserAccount u = account.get();
        var company = companies.findByIdAndDeletedFalse(u.getCompanyId());
        boolean companyActive = company.map(c -> c.getStatus() == ECompanyStatus.ACTIVE).orElse(false);
        if (u.getStatus() != EUserStatus.ACTIVE || !companyActive) {
            keycloak.logout(tokens.refreshToken());
            throw ApiException.unauthorized(EIamError.AUTH_002);
        }
        if (company.get().isEmailVerifyRequired() && company.get().getEmailVerifiedAt() == null) {
            keycloak.logout(tokens.refreshToken());
            throw ApiException.unauthorized(EIamError.AUTH_016);
        }
        if (subscriptions.effective(company.get()).blocked() && !SubscriptionService.mayRenew(
                u.getRoles().stream().filter(r -> !r.isDeleted()).map(r -> r.getCode())
                        .collect(java.util.stream.Collectors.toSet()))) {
            keycloak.logout(tokens.refreshToken());
            throw ApiException.unauthorized(EIamError.AUTH_009);
        }
        try {
            ipRestriction.assertLoginAllowed(u.getCompanyId(), clientIp);
        } catch (ApiException e) {
            keycloak.logout(tokens.refreshToken());
            throw e;
        }
    }

    private static TokenDto toDto(KeycloakTokens t) {
        return new TokenDto(t.accessToken(), t.refreshToken(), t.expiresIn(), t.refreshExpiresIn());
    }
}
