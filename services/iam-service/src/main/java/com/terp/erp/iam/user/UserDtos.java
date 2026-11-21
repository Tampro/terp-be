package com.terp.erp.iam.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserDto(String id, String companyId, String username, String email, String fullName,
                          @Schema(description = "Mobile number, null when not given", example = "+84 90 000 0000")
                          String mobile,
                          EUserStatus status, List<String> roles, Instant createdDate, Instant updatedDate) {
        public static UserDto of(UserAccount u) {
            return new UserDto(u.getId().toString(), u.getCompanyId().toString(), u.getUsername(), u.getEmail(),
                    u.getFullName(), u.getMobile(), u.getStatus(),
                    u.getRoles().stream().filter(r -> !r.isDeleted()).map(Role::getCode).sorted().toList(),
                    u.getCreatedDate(), u.getUpdatedDate());
        }
    }

    /** Returned once at creation: the temporary password is not stored and cannot be read again. */
    public record CreatedUserDto(UserDto user, String temporaryPassword) {
    }

    public record UserCreateForm(
            @Schema(description = "Login name, unique across ERP", example = "hr.demo",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            // '@' and '+' so an email can be the login name (employees are created with their email).
            @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[a-zA-Z0-9._@+-]+") String username,
            @Schema(description = "Email", example = "hr.demo@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Full name", example = "Demo HR", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String fullName,
            @Schema(description = "Role ids of the same company", example = "[\"3\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Set<@NotNull Long> roleIds,
            @Schema(description = "Optional: role codes of the same company, added to roleIds", example = "[\"EMPLOYEE\"]")
            Set<@NotBlank String> roleCodes,
            @Schema(description = "Optional: the first password, chosen by the administrator. Still temporary: the user "
                    + "changes it at the first login. Omitted = generated.")
            @Size(min = 8, max = 128) String password) {
        public UserCreateForm(String username, String email, String fullName, Set<Long> roleIds) {
            this(username, email, fullName, roleIds, null, null);
        }
    }

    public record UserPasswordForm(
            @Schema(description = "New permanent password (realm password policy applies)",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 8, max = 128) String newPassword) {
    }

    public record UserRolesForm(
            @Schema(description = "Full set of role ids; replaces the current roles", example = "[\"3\",\"4\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Set<@NotNull Long> roleIds) {
    }

    /** Edit user: full replacement of the profile; the username never changes. */
    public record UserProfileForm(
            @Schema(example = "Test User", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String fullName,
            @Schema(example = "user@example.invalid", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email
            @Size(max = 255) String email) {
    }

    public record UserStatusForm(
            @Schema(description = "ACTIVE or LOCKED", example = "LOCKED", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EUserStatus status) {
    }
}
