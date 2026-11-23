package com.terp.erp.iam.me;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.util.List;
import java.util.TreeSet;

@Tag(name = "Me")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final com.terp.erp.iam.repository.CompanyRepository companies;
    private final com.terp.erp.iam.saas.SubscriptionService subscriptions;
    private final ProfileService profiles;

    @Operation(summary = "My profile: name, email, avatar")
    @ApiResponses(@ApiResponse(responseCode = "403", description = "No ERP profile (AUTH_006)"))
    @GetMapping("/profile")
    public ApiMessageDto<ProfileService.ProfileDto> profile() {
        return ApiMessageDto.ok(profiles.get());
    }

    @Operation(summary = "Update my profile", description = "Full replacement of name, email and avatar "
            + "(avatarFileId null = none). The username never changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Email used by another account (IAM_017), avatar not a "
                    + "JPG/PNG/GIF of your company up to 2 MB or no access to it (IAM_024)"),
            @ApiResponse(responseCode = "503", description = "Keycloak (AUTH_005) or media-service (IAM_025) "
                    + "unreachable")})
    @org.springframework.web.bind.annotation.PutMapping("/profile")
    public ApiMessageDto<ProfileService.ProfileDto> updateProfile(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            ProfileService.ProfileForm form) {
        return ApiMessageDto.ok(profiles.update(form));
    }

    @Operation(summary = "Change my password", description = "Every session ends afterwards, this one included: "
            + "log in again with the new password.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Current password wrong (AUTH_017), policy not met "
                    + "(AUTH_007), confirmation differs (validation)"),
            @ApiResponse(responseCode = "503", description = "Keycloak unreachable (AUTH_005)")})
    @org.springframework.web.bind.annotation.PutMapping("/password")
    public ApiMessageDto<Void> changePassword(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            ProfileService.PasswordForm form) {
        profiles.changePassword(form);
        return ApiMessageDto.ok(null);
    }

    /** Limits: null = none; storageLimitGb -1 = unlimited too (media-service checks uploads against it). */
    public record CompanyContextDto(String companyId, String name, Integer maxUsers, Integer maxEmployees,
                                    boolean blocked, java.math.BigDecimal storageLimitGb) {
    }

    public record MeDto(String userId, String username, String companyId, boolean superAdmin,
                        List<String> roles, List<String> permissions) {
    }

    @Operation(summary = "Current user, company, roles and permissions",
            description = "The FE hides menus and buttons by `permissions`; the server checks them again on every call.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Signed in but no ERP profile (AUTH_006)")})
    @GetMapping
    public ApiMessageDto<MeDto> me() {
        ErpPrincipal p = requireProfile();
        // Ids are strings on the wire (docs/analysis/04 §4).
        return ApiMessageDto.ok(new MeDto(
                p.userId() == null ? null : p.userId().toString(),
                p.username(),
                p.companyId() == null ? null : p.companyId().toString(),
                p.superAdmin(),
                List.copyOf(new TreeSet<>(p.roles())),
                List.copyOf(new TreeSet<>(p.permissions()))));
    }

    @Operation(summary = "Resolved authorities, for other ERP services",
            description = "Called by RemoteAuthorityResolver with the caller's own token; not meant for the FE.")
    @GetMapping("/authorities")
    public ApiMessageDto<ErpPrincipal> authorities() {
        return ApiMessageDto.ok(requireProfile());
    }

    @Operation(summary = "My company: name and the limits of its effective plan, for other ERP services",
            description = "employee-service checks maxEmployees before creating an employee and prints the name on letters; "
                    + "media-service checks storageLimitGb on uploads. "
                    + "Super admin: companyId names the company.")
    @GetMapping("/company")
    public ApiMessageDto<CompanyContextDto> company(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long companyId) {
        requireProfile();
        Long id = com.terp.erp.security.TenantGuard.resolveCompany(companyId, EIamError.IAM_003);
        var company = companies.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_001));
        var e = subscriptions.effective(company);
        String cid = company.getId().toString();
        return ApiMessageDto.ok(e.plan() == null ? new CompanyContextDto(cid, company.getName(), null, null, e.blocked(),
                null)
                : new CompanyContextDto(cid, company.getName(), e.plan().getMaxUsers(), e.plan().getMaxEmployees(),
                e.blocked(), e.plan().getStorageLimitGb()));
    }

    private static ErpPrincipal requireProfile() {
        ErpPrincipal p = CurrentUser.get();
        // A super admin in "Login as Company" has a company but no ERP account (userId null): that is a profile.
        if (!p.superAdmin() && p.userId() == null && p.companyId() == null) {
            throw ApiException.forbidden(EIamError.AUTH_006);
        }
        return p;
    }
}
