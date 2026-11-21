package com.terp.erp.iam.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.saas.EBillingCycle;
import com.terp.erp.iam.saas.ESubscriptionStatus;
import com.terp.erp.iam.saas.SubscriptionService.Effective;
import com.terp.erp.iam.user.UserDtos.CreatedUserDto;
import com.terp.erp.iam.user.UserDtos.UserDto;

import java.time.Instant;
import java.util.List;

public final class CompanyDtos {
    private CompanyDtos() {
    }

    /** One row of the super admin companies list. Plan fields describe the plan that applies right now. */
    public record CompanyDto(String id, String code, String name, String email, ECompanyStatus status,
                             ESubscriptionStatus subscriptionStatus, String planId, String planCode, String planName,
                             Instant planExpiresAt, Instant createdDate, Instant updatedDate, String referralCode,
                             String referredByCompanyId) {
        static CompanyDto of(Company c, Effective e) {
            return new CompanyDto(c.getId().toString(), c.getCode(), c.getName(), c.getEmail(), c.getStatus(),
                    e.status(),
                    e.plan() == null ? null : e.plan().getId().toString(),
                    e.plan() == null ? null : e.plan().getCode(),
                    e.plan() == null ? null : e.plan().getName(),
                    e.expiresAt(), c.getCreatedDate(), c.getUpdatedDate(), c.getReferralCode(),
                    c.getReferredByCompanyId() == null ? null : c.getReferredByCompanyId().toString());
        }
    }

    @Schema(description = "Counts for the status tabs, with the same keyword/date filter as the list")
    public record StatusCountsDto(long all, long active, long inactive) {
    }

    @Schema(description = "Company info panel")
    public record CompanyInfoDto(CompanyDto company, long userCount, Integer maxUsers, List<UserDto> administrators) {
    }

    public record CompanyCreatedDto(CompanyDto company, CreatedUserDto admin) {
    }

    public record AdminForm(
            @Schema(description = "Login name of the first company administrator", example = "acme.admin",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[a-zA-Z0-9._-]+") String username,
            @Schema(description = "Email", example = "admin@acme.example", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email String email,
            @Schema(description = "Full name", example = "Acme Admin", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String fullName) {
    }

    public record CompanyCreateForm(
            @Schema(description = "Unique code", example = "ACME", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z0-9_]+") String code,
            @Schema(description = "Company name", example = "Acme Co., Ltd", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,
            @Schema(description = "Company contact email", example = "contact@acme.example",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Enable Login: false creates the company SUSPENDED (no one can log in). Default true",
                    example = "true")
            Boolean enableLogin,
            @Schema(description = "First administrator; gets the COMPANY_ADMIN role",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Valid AdminForm admin,
            @Schema(description = "Referral code of the company that referred this one (optional)", example = "REF000002")
            @Size(max = 20) String referralCode) {
    }

    public record CompanyUpdateForm(
            @Schema(description = "Company name", example = "Acme Co., Ltd", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,
            @Schema(description = "Company contact email", example = "contact@acme.example",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email) {
    }

    public record CompanyStatusForm(
            @Schema(description = "ACTIVE (login enabled) or SUSPENDED (login disabled)", example = "SUSPENDED",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ECompanyStatus status) {
    }

    public record UpgradePlanForm(
            @Schema(description = "Plan id", example = "4", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long planId,
            @Schema(description = "MONTHLY or YEARLY", example = "YEARLY", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EBillingCycle billingCycle) {
    }

    public record ResetPasswordForm(
            @Schema(description = "Account of the company to reset; omitted = its first active COMPANY_ADMIN",
                    example = "12") Long userId,
            @Schema(description = "New password; the realm password policy applies", example = "N3w-Passw0rd!",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }
}
