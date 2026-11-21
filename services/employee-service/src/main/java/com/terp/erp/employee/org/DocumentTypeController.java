package com.terp.erp.employee.org;

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
import com.terp.erp.employee.org.DocumentTypeDtos.DocumentTypeDto;
import com.terp.erp.employee.org.DocumentTypeDtos.DocumentTypeForm;
import com.terp.erp.employee.org.DocumentTypeDtos.RequiredForm;

import java.util.List;

@Tag(name = "Document Types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org/document-types")
@SecurityRequirement(name = "bearerAuth")
public class DocumentTypeController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final DocumentTypeService service;

    @Operation(summary = "Document types: the system ones and the company's own (Document Types page)",
            description = "System types first, then by name. keyword matches name or description; required filters "
                    + "Required (true) or Optional (false). required is the value for your company.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DocumentTypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean required,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, required, pageable)));
    }

    @Operation(summary = "Every usable document type, for the Add Employee documents step",
            description = "defaultSlot types are pre-filled; required ones are tracked as missing until uploaded.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.hasAny('ORG_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<DocumentTypeDto>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Document type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such document type in your company (EMP_005)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<DocumentTypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a document type of the company")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping
    public ApiMessageDto<DocumentTypeDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody DocumentTypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a document type of the company (full replacement)",
            description = "name, description and required are replaced (omitted description = cleared, omitted "
                    + "required = Optional). System types: only Required can be set, with PUT /{id}/required.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011), system type (EMP_021)"),
            @ApiResponse(responseCode = "404", description = "No such document type in your company (EMP_005)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<DocumentTypeDto> update(@PathVariable Long id, @Valid @RequestBody DocumentTypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Set a document type Required or Optional for the company",
            description = "Works for system types too: the choice is the company's own, other companies keep theirs.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId for a system type (EMP_019)"),
            @ApiResponse(responseCode = "404", description = "No such document type in your company (EMP_005)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}/required")
    public ApiMessageDto<DocumentTypeDto> required(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @PathVariable Long id, @Valid @RequestBody RequiredForm form) {
        return ApiMessageDto.ok(service.setRequired(companyId, id, form.required()));
    }

    @Operation(summary = "Delete a document type of the company", description = "Refused while a document uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (EMP_010), system type (EMP_021)"),
            @ApiResponse(responseCode = "404", description = "No such document type in your company (EMP_005)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
