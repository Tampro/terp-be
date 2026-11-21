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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.settings.CompanyEmailService.CompanyEmailForm;
import com.terp.erp.iam.settings.CompanyEmailService.CompanyEmailView;
import com.terp.erp.iam.settings.SettingsDtos.TestEmailForm;

@Tag(name = "Company email settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/company/email")
@SecurityRequirement(name = "bearerAuth")
public class CompanyEmailController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NO_COMPANY = "Super admin without companyId (IAM_003)";

    private final CompanyEmailService service;

    @Operation(summary = "The company's email settings", description = "configured = it has its own SMTP; "
            + "usingPlatform = the platform SMTP sends its emails. The password is never returned (passwordSet).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping
    public ApiMessageDto<CompanyEmailView> get(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Save the company's SMTP", description = "Full replacement; password omitted = kept, empty = "
            + "removed (encrypted with ERP_SETTINGS_ENC_KEY). enabled = false keeps the settings but uses the "
            + "platform SMTP.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Encryption key not configured (SETTINGS_002), "
            + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping
    public ApiMessageDto<CompanyEmailView> save(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                @Valid @RequestBody CompanyEmailForm form) {
        return ApiMessageDto.ok(service.save(companyId, form));
    }

    @Operation(summary = "Back to the platform SMTP", description = "Removes the company's SMTP settings.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @DeleteMapping
    public ApiMessageDto<Void> delete(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        service.delete(companyId);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Send Test Email", description = "Through the company's own SMTP.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "No company SMTP (SETTINGS_003), sending failed "
            + "with the server's reason (SETTINGS_004), stored password unreadable (SETTINGS_007), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping("/test")
    public ApiMessageDto<Void> test(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                    @Valid @RequestBody TestEmailForm form) {
        service.sendTest(companyId, form.to());
        return ApiMessageDto.ok(null);
    }
}
