package com.terp.erp.iam.me;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

/**
 * Profile Settings (docs/analysis/06 §3, also on the company side): one's own name, email and avatar, and password.
 * The super admin is a Keycloak-only account: name and email live in Keycloak and there is no avatar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserAccountRepository users;
    private final KeycloakClient keycloak;
    private final AvatarMediaClient media;

    /** Digits, + ( ) - . and spaces, 3 to 30 characters — the same rule as the company mobile number. */
    static final String MOBILE_PATTERN = "^$|[0-9+()\\-. ]{3,30}";

    public record ProfileDto(String username, String fullName, String email,
                             @Schema(description = "Mobile number; null when not given (always null for the super admin)",
                                     example = "+84 90 000 0000") String mobile,
                             @Schema(description = "Media file id of the avatar") String avatarFileId,
                             @Schema(description = "Signed link, valid a few minutes") String avatarUrl,
                             boolean superAdmin) {
    }

    public record ProfileForm(
            @Schema(example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String fullName,
            @Schema(example = "a.nguyen@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Mobile number with country code; null or blank = none. Not for the super admin.",
                    example = "+84 90 000 0000")
            @Size(max = 30) @jakarta.validation.constraints.Pattern(regexp = MOBILE_PATTERN,
                    message = "mobile may contain only digits, + ( ) - . and spaces (3 to 30 characters)") String mobile,
            @Schema(description = "Media file id (JPG, PNG or GIF of your company, max 2 MB); null = no avatar. "
                    + "Not for the super admin.")
            Long avatarFileId) {
    }

    public record PasswordForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 128) String currentPassword,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 8, max = 128) String newPassword,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 128) String confirmPassword) {
        @Schema(hidden = true)
        @AssertTrue(message = "confirmPassword must equal newPassword")
        public boolean isPasswordConfirmed() {
            return newPassword == null || newPassword.equals(confirmPassword);
        }
    }

    @Transactional(readOnly = true)
    public ProfileDto get() {
        ErpPrincipal p = CurrentUser.get();
        if (p.superAdmin()) {
            return new ProfileDto(p.username(), claim("name"), claim("email"), null, null, null, true);
        }
        UserAccount u = mine(p);
        return dto(u, media.viewUrl(u.getAvatarMediaId()));
    }

    /** Full replacement of name, email, mobile and avatar. The username (login name) never changes. */
    @Transactional
    public ProfileDto update(ProfileForm f) {
        ErpPrincipal p = CurrentUser.get();
        String fullName = f.fullName().trim();
        String email = f.email().trim();
        String mobile = f.mobile() == null || f.mobile().isBlank() ? null : f.mobile().trim();
        if (p.superAdmin()) {
            if (f.avatarFileId() != null) {
                throw ApiException.badRequest(EIamError.IAM_024);
            }
            // The super admin lives only in Keycloak; refusing beats silently dropping the number.
            if (mobile != null) {
                throw ApiException.badRequest(EIamError.IAM_031);
            }
            keycloak.updateProfile(p.keycloakId(), email, fullName);
            return new ProfileDto(p.username(), fullName, email, null, null, null, true);
        }
        UserAccount u = mine(p);
        String avatarUrl = f.avatarFileId() == null ? null
                : media.requireAvatar(f.avatarFileId(), u.getCompanyId()).viewUrl();
        keycloak.updateProfile(u.getKeycloakId(), email, fullName);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setMobile(mobile);
        u.setAvatarMediaId(f.avatarFileId());
        return dto(u, avatarUrl);
    }

    /**
     * The current password is proven with a password grant (its session is closed at once). Afterwards every
     * session ends, this one included: the FE logs in again with the new password.
     */
    public void changePassword(PasswordForm f) {
        ErpPrincipal p = CurrentUser.get();
        try {
            var tokens = keycloak.passwordGrant(p.username(), f.currentPassword());
            keycloak.logout(tokens.refreshToken());
        } catch (ApiException e) {
            if (e.getErrorCode() == EIamError.AUTH_001) {
                throw ApiException.badRequest(EIamError.AUTH_017);
            }
            throw e;
        }
        keycloak.setPermanentPassword(p.keycloakId(), f.newPassword());
        keycloak.logoutSessions(p.keycloakId());
        log.info("User {} changed their password", p.userId() == null ? "super-admin" : p.userId());
    }

    private UserAccount mine(ErpPrincipal p) {
        if (p.userId() == null) {
            throw ApiException.forbidden(EIamError.AUTH_006);
        }
        return users.findByIdAndDeletedFalse(p.userId()).orElseThrow(() -> ApiException.forbidden(EIamError.AUTH_006));
    }

    private static ProfileDto dto(UserAccount u, String avatarUrl) {
        return new ProfileDto(u.getUsername(), u.getFullName(), u.getEmail(), u.getMobile(),
                u.getAvatarMediaId() == null ? null : u.getAvatarMediaId().toString(), avatarUrl, false);
    }

    private static String claim(String name) {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString(name) : null;
    }
}
