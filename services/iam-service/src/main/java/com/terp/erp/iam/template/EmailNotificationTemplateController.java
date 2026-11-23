package com.terp.erp.iam.template;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.template.NotificationTemplateService.EmailGroup;
import com.terp.erp.iam.template.NotificationTemplateService.EmailPreview;
import com.terp.erp.iam.template.NotificationTemplateService.EmailPreviewForm;
import com.terp.erp.iam.template.NotificationTemplateService.EmailTemplateForm;
import com.terp.erp.iam.template.NotificationTemplateService.EmailTemplateView;
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

import java.util.List;

/** Company Settings → Email templates (ERP manual 3.5): the business emails, edit only, per language. */
@Tag(name = "Email notification templates (company)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/email-notification-templates")
@SecurityRequirement(name = "bearerAuth")
public class EmailNotificationTemplateController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String LANG = "vi, en or ko";
    private static final String NO_COMPANY = "super admin without companyId (IAM_003)";

    private final NotificationTemplateService service;

    @Operation(summary = "Business email templates, grouped by module, with the languages my company customised",
            description = "customised=false: the built-in default text is sent for every language.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "No company (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @GetMapping
    public ApiMessageDto<List<EmailGroup>> list(@Parameter(description = COMPANY) @RequestParam(required = false)
                                                Long companyId) {
        return ApiMessageDto.ok(service.emailList(companyId));
    }

    @Operation(summary = "One template: variables and my company's text per language (null = default)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No company (IAM_003)"),
            @ApiResponse(responseCode = "404", description = "Unknown code (EMAIL_001)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @GetMapping("/{code}")
    public ApiMessageDto<EmailTemplateView> get(@PathVariable String code,
                                                @Parameter(description = COMPANY) @RequestParam(required = false)
                                                Long companyId) {
        return ApiMessageDto.ok(service.emailGet(companyId, code));
    }

    @Operation(summary = "Save my company's text for one language",
            description = "Full replacement of subject + body for that language. The body is sanitised HTML; "
                    + "placeholders must be in variables; payload values are HTML-escaped when sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unsupported language (EMAIL_002), unknown placeholder "
                    + "(SETTINGS_025), validation (COMMON_001), " + NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Unknown code (EMAIL_001)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @PutMapping("/{code}/{lang}")
    public ApiMessageDto<EmailTemplateView> save(@PathVariable String code,
                                                 @Parameter(description = LANG) @PathVariable String lang,
                                                 @Parameter(description = COMPANY) @RequestParam(required = false)
                                                 Long companyId,
                                                 @Valid @RequestBody EmailTemplateForm form) {
        return ApiMessageDto.ok(service.emailSave(companyId, code, lang, form));
    }

    @Operation(summary = "Reset one language to the built-in default", description = "Deletes the override; "
            + "no override = no change.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unsupported language (EMAIL_002), " + NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Unknown code (EMAIL_001)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @DeleteMapping("/{code}/{lang}")
    public ApiMessageDto<EmailTemplateView> reset(@PathVariable String code,
                                                  @Parameter(description = LANG) @PathVariable String lang,
                                                  @Parameter(description = COMPANY) @RequestParam(required = false)
                                                  Long companyId) {
        return ApiMessageDto.ok(service.emailReset(companyId, code, lang));
    }

    @Operation(summary = "Render with sample values [key], nothing is sent",
            description = "The body in the request (sanitised first), else my company's saved text.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Nothing to preview (SETTINGS_023), unsupported "
                    + "language (EMAIL_002), " + NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Unknown code (EMAIL_001)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @PostMapping("/{code}/{lang}/preview")
    public ApiMessageDto<EmailPreview> preview(@PathVariable String code,
                                               @Parameter(description = LANG) @PathVariable String lang,
                                               @Parameter(description = COMPANY) @RequestParam(required = false)
                                               Long companyId,
                                               @Valid @RequestBody(required = false) EmailPreviewForm form) {
        return ApiMessageDto.ok(service.emailPreview(companyId, code, lang, form));
    }
}
