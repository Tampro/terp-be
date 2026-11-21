package com.terp.erp.iam.company;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.terp.erp.iam.company.CompanyDtos.CompanyCreateForm;
import com.terp.erp.iam.company.CompanyDtos.CompanyCreatedDto;
import com.terp.erp.iam.company.CompanyDtos.CompanyDto;
import com.terp.erp.iam.company.CompanyDtos.CompanyInfoDto;
import com.terp.erp.iam.company.CompanyDtos.CompanyStatusForm;
import com.terp.erp.iam.company.CompanyDtos.CompanyUpdateForm;
import com.terp.erp.iam.company.CompanyDtos.ResetPasswordForm;
import com.terp.erp.iam.company.CompanyDtos.StatusCountsDto;
import com.terp.erp.iam.company.CompanyDtos.UpgradePlanForm;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.saas.SaasDtos.OrderDto;
import com.terp.erp.iam.user.UserDtos.UserDto;

import java.time.LocalDate;

/**
 * Super admin companies list (docs/analysis/06 §3.2.1). "Login as Company" is deliberately not here — postponed to
 * its own task (impersonation needs token exchange and an audit trail).
 */
@Tag(name = "Companies (super admin)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/companies")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@PermissionChecker.isSuperAdmin()")
public class CompanyController {
    private final CompanyService companyService;

    @Operation(summary = "Companies list",
            description = "keyword matches name, code or email; createdFrom/createdTo are inclusive UTC days; "
                    + "status ACTIVE = tab Active, SUSPENDED = tab Inactive, omitted = All. Plan columns show the plan "
                    + "that applies now (after expiry fallback).")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CompanyDto>> list(
            @RequestParam(required = false) String keyword,
            @Parameter(example = "2026-11-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @Parameter(example = "2026-11-30") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) ECompanyStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                companyService.search(keyword, createdFrom, createdTo, status, pageable)));
    }

    @Operation(summary = "Counts for the status tabs (All / Active / Inactive)",
            description = "Same keyword and date filter as the list.")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCountsDto> statusCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {
        return ApiMessageDto.ok(companyService.countByStatus(keyword, createdFrom, createdTo));
    }

    @Operation(summary = "Company row")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such company (IAM_001)"))
    @GetMapping("/{id}")
    public ApiMessageDto<CompanyDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(companyService.get(id));
    }

    @Operation(summary = "Company info panel", description = "Profile, subscription, account count vs plan limit, administrators.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such company (IAM_001)"))
    @GetMapping("/{id}/info")
    public ApiMessageDto<CompanyInfoDto> info(@PathVariable Long id) {
        return ApiMessageDto.ok(companyService.info(id));
    }

    @Operation(summary = "Add a company",
            description = "Creates the company with its default roles, the default plan and the first administrator, "
                    + "whose temporary password is returned once. enableLogin = false creates it SUSPENDED.")
    @ApiResponses(@ApiResponse(responseCode = "400",
            description = "Code taken (IAM_002), username taken (IAM_010), Keycloak error (IAM_014)"))
    @PostMapping
    public ApiMessageDto<CompanyCreatedDto> create(@Valid @RequestBody CompanyCreateForm form) {
        return ApiMessageDto.ok(companyService.create(form));
    }

    @Operation(summary = "Edit a company (full replacement of name and email; code is fixed)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such company (IAM_001)"))
    @PutMapping("/{id}")
    public ApiMessageDto<CompanyDto> update(@PathVariable Long id, @Valid @RequestBody CompanyUpdateForm form) {
        return ApiMessageDto.ok(companyService.update(id, form));
    }

    @Operation(summary = "Disable / enable login", description = "SUSPENDED: nobody of the company can log in and "
            + "issued tokens are rejected within 30 seconds.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such company (IAM_001)"))
    @PutMapping("/{id}/status")
    public ApiMessageDto<CompanyDto> status(@PathVariable Long id, @Valid @RequestBody CompanyStatusForm form) {
        return ApiMessageDto.ok(companyService.changeStatus(id, form.status()));
    }

    @Operation(summary = "Upgrade plan", description = "Moves the company to the plan for one billing cycle, starting "
            + "now (or extending the running period of the same plan). Recorded as an APPROVED MANUAL order.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Plan has no yearly price (SAAS_003)"),
            @ApiResponse(responseCode = "404", description = "No such company (IAM_001) or plan (SAAS_001)")})
    @PutMapping("/{id}/plan")
    public ApiMessageDto<OrderDto> upgradePlan(@PathVariable Long id, @Valid @RequestBody UpgradePlanForm form) {
        return ApiMessageDto.ok(companyService.upgradePlan(id, form.planId(), form.billingCycle()));
    }

    @Operation(summary = "Reset password", description = "Sets a permanent password for an account of the company "
            + "(default: its first active COMPANY_ADMIN) and ends that account's sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Password policy not met (AUTH_007), no active "
                    + "administrator (IAM_016)"),
            @ApiResponse(responseCode = "404", description = "No such company (IAM_001) or user in it (IAM_009)")})
    @PostMapping("/{id}/reset-password")
    public ApiMessageDto<UserDto> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordForm form) {
        return ApiMessageDto.ok(companyService.resetPassword(id, form.userId(), form.newPassword()));
    }

    @Operation(summary = "Delete a company (soft)", description = "Keeps its data, removes every account from "
            + "Keycloak (their usernames become free; sessions end), cancels pending orders and frees the code. "
            + "Cannot be undone through the API.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "No such company (IAM_001)"),
            @ApiResponse(responseCode = "503", description = "Keycloak unreachable (AUTH_005)")})
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return ApiMessageDto.ok(null);
    }
}
