package com.terp.erp.iam.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.user.UserDtos.CreatedUserDto;
import com.terp.erp.iam.user.UserDtos.UserCreateForm;
import com.terp.erp.iam.user.UserDtos.UserDto;
import com.terp.erp.iam.user.UserDtos.UserPasswordForm;
import com.terp.erp.iam.user.UserDtos.UserProfileForm;
import com.terp.erp.iam.user.UserDtos.UserRolesForm;
import com.terp.erp.iam.user.UserDtos.UserStatusForm;

@Tag(name = "Users")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private static final String COMPANY_PARAM = "Super admin only: the company to act on. Ignored for company users.";

    private final UserService userService;

    @Operation(summary = "Accounts of the caller's company")
    @PreAuthorize("@PermissionChecker.has('IAM_USER_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<UserDto>> list(
            @Parameter(description = COMPANY_PARAM) @RequestParam(required = false) Long companyId,
            @Parameter(description = "Matches username, full name or email") @RequestParam(required = false) String keyword,
            @Parameter(description = "Only accounts holding this role") @RequestParam(required = false) Long roleId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserDto> page = userService.search(companyId, keyword, roleId, pageable);
        return ApiMessageDto.ok(ResponseListDto.of(page));
    }

    @Operation(summary = "Account detail")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such user in your company (IAM_009)"))
    @PreAuthorize("@PermissionChecker.has('IAM_USER_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<UserDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(userService.get(id));
    }

    @Operation(summary = "Create an account",
            description = "Creates the Keycloak user with a temporary password, returned once in this response. "
                    + "The user sets a new one through POST /api/v1/auth/initial-password.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Username taken (IAM_010), role not in company "
                    + "(IAM_011), Keycloak error (IAM_014), no company (IAM_003)"),
            @ApiResponse(responseCode = "403", description = "Role grants permissions you do not hold (IAM_015)")})
    @PreAuthorize("@PermissionChecker.has('IAM_USER_CREATE')")
    @PostMapping
    public ApiMessageDto<CreatedUserDto> create(
            @Parameter(description = COMPANY_PARAM) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody UserCreateForm form) {
        return ApiMessageDto.ok(userService.create(companyId, form));
    }

    @Operation(summary = "Replace the roles of an account")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Role not in company (IAM_011), own account (IAM_013)"),
            @ApiResponse(responseCode = "403", description = "Role grants permissions you do not hold (IAM_015)"),
            @ApiResponse(responseCode = "404", description = "No such user in your company (IAM_009)")})
    @PreAuthorize("@PermissionChecker.has('IAM_USER_UPDATE')")
    @PutMapping("/{id}/roles")
    public ApiMessageDto<UserDto> roles(@PathVariable Long id, @Valid @RequestBody UserRolesForm form) {
        return ApiMessageDto.ok(userService.replaceRoles(id, form.roleIds()));
    }

    @Operation(summary = "Set a new password for an account",
            description = "Permanent password; pending first-login change is cleared. Not for your own account.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Own account (IAM_013), password policy (AUTH_007)"),
            @ApiResponse(responseCode = "404", description = "No such user in your company (IAM_009)")})
    @PreAuthorize("@PermissionChecker.has('IAM_USER_UPDATE')")
    @PutMapping("/{id}/password")
    public ApiMessageDto<UserDto> password(@PathVariable Long id, @Valid @RequestBody UserPasswordForm form) {
        return ApiMessageDto.ok(userService.changePassword(id, form.newPassword()));
    }

    @Operation(summary = "Lock or unlock an account",
            description = "LOCKED disables the Keycloak user and ends its sessions; access tokens already issued are "
                    + "rejected by IAM within 30 seconds.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Own account (IAM_013)"),
            @ApiResponse(responseCode = "404", description = "No such user in your company (IAM_009)")})
    @PreAuthorize("@PermissionChecker.has('IAM_USER_UPDATE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<UserDto> status(@PathVariable Long id, @Valid @RequestBody UserStatusForm form) {
        return ApiMessageDto.ok(userService.changeStatus(id, form.status()));
    }

    @Operation(summary = "Edit a user", description = "Full name and email (full replacement), in Keycloak too; the "
            + "username never changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Own account (IAM_013), email used by another account "
                    + "(IAM_017)"),
            @ApiResponse(responseCode = "404", description = "No such user in your company (IAM_009)"),
            @ApiResponse(responseCode = "503", description = "Keycloak unreachable (AUTH_005)")})
    @PreAuthorize("@PermissionChecker.has('IAM_USER_UPDATE')")
    @PutMapping("/{id}")
    public ApiMessageDto<UserDto> update(@PathVariable Long id, @Valid @RequestBody UserProfileForm form) {
        return ApiMessageDto.ok(userService.updateProfile(id, form));
    }

    @Operation(summary = "Delete a user", description = "Soft-deleted here and removed from Keycloak: every session "
            + "ends and the username can be used again.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Own account (IAM_013), the last active company "
                    + "administrator (IAM_018)"),
            @ApiResponse(responseCode = "404", description = "No such user in your company (IAM_009)"),
            @ApiResponse(responseCode = "503", description = "Keycloak unreachable (AUTH_005)")})
    @PreAuthorize("@PermissionChecker.has('IAM_USER_UPDATE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiMessageDto.ok(null);
    }
}
