package com.terp.erp.document.category;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import com.terp.erp.document.category.DocumentCategoryService.CategoryDto;
import com.terp.erp.document.category.DocumentCategoryService.CategoryForm;
import com.terp.erp.document.category.DocumentCategoryService.Option;
import com.terp.erp.document.common.EStatus;

import java.util.List;

import static com.terp.erp.document.common.Texts.COMPANY;

@Tag(name = "Document categories")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/document/categories")
@SecurityRequirement(name = "bearerAuth")
public class DocumentCategoryController {
    private static final String NOT_FOUND = "No such document category in your company (DOC_002)";
    private static final String NO_COMPANY = "Super admin without companyId (DOC_001)";

    private final DocumentCategoryService service;

    public record StatusForm(@NotNull EStatus status) {
    }

    @Operation(summary = "Document categories of the company", description = "keyword = name or description; "
            + "filters: status, mandatory (the Type filter: true = Mandatory, false = Optional).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CategoryDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @RequestParam(required = false) Boolean mandatory,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, mandatory, pageable)));
    }

    @Operation(summary = "Category dropdown", description = "Active categories, with mandatory (it pre-ticks "
            + "Requires Acknowledgement).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Category details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CategoryDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add New Category")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (DOC_003), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<CategoryDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a category", description = "Full replacement; mandatory and status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (DOC_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CategoryDto> update(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Deactivate / activate a category")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<CategoryDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a category", description = "Only a category without documents.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The category has documents (DOC_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
