package com.terp.erp.employee.certificate;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.employee.certificate.LetterTemplateService.LetterTemplateForm;
import com.terp.erp.employee.certificate.LetterTemplateService.LetterTemplateView;

@Tag(name = "Letter templates", description = "NOC, Experience Certificate and Joining Letter settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/employees/letter-templates")
@SecurityRequirement(name = "bearerAuth")
public class LetterTemplateController {
    private static final String TYPE = "JOINING_LETTER, EXPERIENCE_CERTIFICATE or NOC";
    private static final String LANG = "vi (default), en or ko";
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String ERRORS = "Unknown type (EMP_025), unsupported language (EMP_094), super admin "
            + "without companyId (EMP_019)";

    private final LetterTemplateService service;

    @Operation(summary = "The letter's text in one language", description = "The company's own text, or the "
            + "built-in default (customized = false), with the placeholders it may use.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/{type}")
    public ApiMessageDto<LetterTemplateView> get(
            @Parameter(description = TYPE) @PathVariable String type,
            @Parameter(description = LANG) @RequestParam(required = false) String lang,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(type, lang, companyId));
    }

    @Operation(summary = "Save the letter's text", description = "Full replacement of the company's text for this "
            + "letter and language. The HTML is sanitized: formatting tags only, of styles only text-align.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = ERRORS + ", empty content (validation)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/{type}")
    public ApiMessageDto<LetterTemplateView> save(
            @Parameter(description = TYPE) @PathVariable String type,
            @Parameter(description = LANG) @RequestParam(required = false) String lang,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody LetterTemplateForm form) {
        return ApiMessageDto.ok(service.save(type, lang, companyId, form));
    }

    @Operation(summary = "Back to the default text", description = "Removes the company's text for this letter and "
            + "language; returns the default.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @DeleteMapping("/{type}")
    public ApiMessageDto<LetterTemplateView> reset(
            @Parameter(description = TYPE) @PathVariable String type,
            @Parameter(description = LANG) @RequestParam(required = false) String lang,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.reset(type, lang, companyId));
    }
}
