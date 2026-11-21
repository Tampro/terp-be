package com.terp.erp.iam.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Role;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class RoleDtos {
    private RoleDtos() {
    }

    public record PermissionDto(String code, String module, String description) {
        static PermissionDto of(Permission p) {
            return new PermissionDto(p.getCode(), p.getModule(), p.getDescription());
        }
    }

    public record RoleDto(String id, String companyId, String code, String name, String description,
                          boolean systemRole, List<String> permissions, Instant createdDate, Instant updatedDate) {
        static RoleDto of(Role r) {
            return new RoleDto(r.getId().toString(), r.getCompanyId().toString(), r.getCode(), r.getName(),
                    r.getDescription(), r.isSystemRole(),
                    r.getPermissions().stream().map(Permission::getCode).sorted().toList(),
                    r.getCreatedDate(), r.getUpdatedDate());
        }
    }

    public record RoleForm(
            @Schema(description = "Unique in the company, UPPER_SNAKE_CASE; ignored on update of a system role",
                    example = "TEAM_LEAD", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
            @Schema(description = "Display name", example = "Team lead", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Optional description; cleared when omitted on update", example = "Leads a squad")
            @Size(max = 255) String description,
            @Schema(description = "Full set of permission codes; replaces the current set",
                    example = "[\"IAM_USER_VIEW\"]", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Set<@NotBlank String> permissions) {
    }
}
