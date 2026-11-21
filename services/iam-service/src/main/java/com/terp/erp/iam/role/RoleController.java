package com.terp.erp.iam.role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.role.RoleDtos.PermissionDto;
import com.terp.erp.iam.role.RoleDtos.RoleDto;
import com.terp.erp.iam.role.RoleDtos.RoleForm;

import java.util.List;

@Tag(name = "Roles & permissions")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {
    private static final String COMPANY_PARAM = "Super admin only: the company to act on. Ignored for company users.";

    private final RoleService roleService;

    @Operation(summary = "Permission catalog")
    @PreAuthorize("@PermissionChecker.has('IAM_PERMISSION_VIEW')")
    @GetMapping("/permissions")
    public ApiMessageDto<List<PermissionDto>> permissions() {
        return ApiMessageDto.ok(roleService.listPermissions());
    }

    @Operation(summary = "Roles of the caller's company")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_ROLE_VIEW')")
    @GetMapping("/roles")
    public ApiMessageDto<List<RoleDto>> list(@Parameter(description = COMPANY_PARAM) @RequestParam(required = false) Long companyId,
                                             @Parameter(description = "Matches the role name or code")
                                             @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(roleService.list(companyId, keyword));
    }

    @Operation(summary = "Role detail")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such role in your company (IAM_004)"))
    @PreAuthorize("@PermissionChecker.has('IAM_ROLE_VIEW')")
    @GetMapping("/roles/{id}")
    public ApiMessageDto<RoleDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(roleService.get(id));
    }

    @Operation(summary = "Create a role")
    @ApiResponses(@ApiResponse(responseCode = "400",
            description = "Code taken (IAM_005), unknown permission (IAM_008), no company (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_ROLE_CREATE')")
    @PostMapping("/roles")
    public ApiMessageDto<RoleDto> create(@Parameter(description = COMPANY_PARAM) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody RoleForm form) {
        return ApiMessageDto.ok(roleService.create(companyId, form));
    }

    @Operation(summary = "Update a role (full replacement)",
            description = "PUT replaces everything: an omitted description is cleared and `permissions` becomes the "
                    + "exact set. A system role keeps its code; COMPANY_ADMIN keeps every IAM permission.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Code taken (IAM_005), system role code change "
                    + "(IAM_006), unknown permission (IAM_008), admin role reduced (IAM_012)"),
            @ApiResponse(responseCode = "404", description = "No such role in your company (IAM_004)")})
    @PreAuthorize("@PermissionChecker.has('IAM_ROLE_UPDATE')")
    @PutMapping("/roles/{id}")
    public ApiMessageDto<RoleDto> update(@PathVariable Long id, @Valid @RequestBody RoleForm form) {
        return ApiMessageDto.ok(roleService.update(id, form));
    }

    @Operation(summary = "Delete a role (soft)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "System role (IAM_006), still assigned (IAM_007)"),
            @ApiResponse(responseCode = "404", description = "No such role in your company (IAM_004)")})
    @PreAuthorize("@PermissionChecker.has('IAM_ROLE_DELETE')")
    @DeleteMapping("/roles/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiMessageDto.ok(null);
    }
}
