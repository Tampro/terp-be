package com.terp.erp.iam.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.settings.CompanySettingsService.CompanyBrand;
import com.terp.erp.iam.settings.CompanySettingsService.CompanyCurrency;
import com.terp.erp.iam.settings.CompanySettingsService.CompanySystem;
import com.terp.erp.iam.settings.CompanySettingsService.EffectiveSettings;

@Tag(name = "Company settings", description = "System, Brand and Currency settings of the company")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/company")
@SecurityRequirement(name = "bearerAuth")
public class CompanySettingsController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NO_COMPANY = "Super admin without companyId (IAM_003)";
    private static final String OVERRIDES = "Only the fields the company overrides; null = the platform's value.";

    private final CompanySettingsService service;

    @Operation(summary = "Settings that apply to the company", description = "Its own value where set, else the "
            + "platform's: language, date/time format, time zone, brand, currency and number format. For every user.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/effective")
    public ApiMessageDto<EffectiveSettings> effective(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.effective(companyId));
    }

    @Operation(summary = "Company System Settings", description = OVERRIDES)
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/system")
    public ApiMessageDto<CompanySystem> system(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.system(companyId));
    }

    @Operation(summary = "Save Company System Settings", description = "Full replacement of the overrides. " + OVERRIDES)
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Language missing or disabled (SETTINGS_008), "
            + "unknown time zone (SETTINGS_001), validation, " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/system")
    public ApiMessageDto<CompanySystem> saveSystem(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                   @Valid @RequestBody CompanySystem form) {
        return ApiMessageDto.ok(service.saveSystem(companyId, form));
    }

    @Operation(summary = "Company Brand Settings", description = OVERRIDES)
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/brand")
    public ApiMessageDto<CompanyBrand> brand(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.brand(companyId));
    }

    @Operation(summary = "Save Company Brand Settings", description = "Full replacement of the overrides. Logos are "
            + "media URLs. " + OVERRIDES)
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Validation, " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/brand")
    public ApiMessageDto<CompanyBrand> saveBrand(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                 @Valid @RequestBody CompanyBrand form) {
        return ApiMessageDto.ok(service.saveBrand(companyId, form));
    }

    @Operation(summary = "Company Currency Settings", description = OVERRIDES)
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/currency")
    public ApiMessageDto<CompanyCurrency> currency(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.currency(companyId));
    }

    @Operation(summary = "Save Company Currency Settings", description = "Full replacement of the overrides. "
            + "currencyCode: one of the Currency page. " + OVERRIDES)
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unknown currency (SETTINGS_009), validation, "
            + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/currency")
    public ApiMessageDto<CompanyCurrency> saveCurrency(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                       @Valid @RequestBody CompanyCurrency form) {
        return ApiMessageDto.ok(service.saveCurrency(companyId, form));
    }
}
