package com.terp.erp.iam.email;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.email.EmailTemplateService.ContentForm;
import com.terp.erp.iam.email.EmailTemplateService.FromNameForm;
import com.terp.erp.iam.email.EmailTemplateService.Rendered;
import com.terp.erp.iam.email.EmailTemplateService.TemplateDetail;
import com.terp.erp.iam.email.EmailTemplateService.TemplateRow;

/** System Control → Email Templates (docs/analysis/06 §3.3.1), super admin only. */
@Tag(name = "Email templates (super admin)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/admin/email-templates")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@PermissionChecker.isSuperAdmin()")
public class EmailTemplateController {
    private final EmailTemplateService templates;

    @Operation(summary = "Templates list", description = "keyword matches template name or From Name; sort by name, "
            + "fromName or createdDate; size 10/25/50/100.")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TemplateRow>> list(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(templates.search(keyword, pageable)));
    }

    @Operation(summary = "Edit page: settings, content per language, available variables")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such template (EMAIL_001)"))
    @GetMapping("/{id}")
    public ApiMessageDto<TemplateDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(templates.get(id));
    }

    @Operation(summary = "Save the From Name (template name is fixed)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such template (EMAIL_001)"))
    @PutMapping("/{id}/settings")
    public ApiMessageDto<TemplateDetail> saveFromName(@PathVariable Long id, @Valid @RequestBody FromNameForm form) {
        return ApiMessageDto.ok(templates.saveFromName(id, form.fromName()));
    }

    @Operation(summary = "Save subject and body for one language (vi, en, ko)",
            description = "Values put into placeholders are HTML-escaped when sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unsupported language (EMAIL_002)"),
            @ApiResponse(responseCode = "404", description = "No such template (EMAIL_001)")})
    @PutMapping("/{id}/content/{lang}")
    public ApiMessageDto<TemplateDetail> saveContent(@PathVariable Long id, @PathVariable String lang,
                                                     @Valid @RequestBody ContentForm form) {
        return ApiMessageDto.ok(templates.saveContent(id, lang, form));
    }

    @Operation(summary = "Preview with sample values ([variable_name])")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unsupported language or no text yet (EMAIL_002)"),
            @ApiResponse(responseCode = "404", description = "No such template (EMAIL_001)")})
    @GetMapping("/{id}/preview/{lang}")
    public ApiMessageDto<Rendered> preview(@PathVariable Long id, @PathVariable String lang) {
        return ApiMessageDto.ok(templates.preview(id, lang));
    }
}
