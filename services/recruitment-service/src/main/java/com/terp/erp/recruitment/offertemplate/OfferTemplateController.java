package com.terp.erp.recruitment.offertemplate;

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
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.GenerateForm;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.Generated;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.StatusCounts;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.StatusForm;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.TemplateDto;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.TemplateForm;

@Tag(name = "Offer Templates")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/offer-templates")
@SecurityRequirement(name = "bearerAuth")
public class OfferTemplateController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final OfferTemplateService service;

    @Operation(summary = "Offer templates of the company", description = "keyword matches the name; contentPreview "
            + "is plain text, at most 150 characters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TemplateDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword));
    }

    @Operation(summary = "Template details (View / Preview)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such template in your company (REC_032)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TemplateDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a template", description = "Content is sanitized HTML with {{variable_name}} "
            + "placeholders; variables is a comma-separated list of names without braces.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (REC_004), invalid variable (REC_033), "
            + "super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<TemplateDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                             Long companyId, @Valid @RequestBody TemplateForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a template", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (REC_004), invalid variable (REC_033)"),
            @ApiResponse(responseCode = "404", description = "No such template in your company (REC_032)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TemplateDto> update(@PathVariable Long id, @Valid @RequestBody TemplateForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Toggle Status (Active / Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such template in your company (REC_032)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<TemplateDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a template")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such template in your company (REC_032)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Generate Offer: the letter with every variable filled",
            description = "Values are HTML-escaped; nothing is saved. Every declared variable needs a value.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "A variable without a value (REC_034)"),
            @ApiResponse(responseCode = "404", description = "No such template in your company (REC_032)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping("/{id}/generate")
    public ApiMessageDto<Generated> generate(@PathVariable Long id, @Valid @RequestBody GenerateForm form) {
        return ApiMessageDto.ok(service.generate(id, form.values()));
    }
}
