package com.terp.erp.recruitment.career;

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
import com.terp.erp.recruitment.career.CareerSettingsService.SettingsDto;
import com.terp.erp.recruitment.career.CareerSettingsService.SettingsForm;

@Tag(name = "Career Settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/career-settings")
@SecurityRequirement(name = "bearerAuth")
public class CareerSettingsController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final CareerSettingsService service;

    @Operation(summary = "The company's career page settings", description = "data is null until they are saved.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<SettingsDto> get(@Parameter(description = COMPANY) @RequestParam(required = false)
                                          Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Save the career page settings", description = "Full replacement. The page is "
            + "/api/v1/public/career/{slug}; changing the slug breaks old links.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Invalid slug (REC_051), slug taken (REC_004), "
            + "super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping
    public ApiMessageDto<SettingsDto> save(@Parameter(description = COMPANY) @RequestParam(required = false)
                                           Long companyId, @Valid @RequestBody SettingsForm form) {
        return ApiMessageDto.ok(service.save(companyId, form));
    }
}
