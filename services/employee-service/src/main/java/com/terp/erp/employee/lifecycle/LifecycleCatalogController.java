package com.terp.erp.employee.lifecycle;

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
import com.terp.erp.employee.lifecycle.LifecycleCatalogService.CatalogDto;
import com.terp.erp.employee.lifecycle.LifecycleCatalogService.CatalogForm;
import com.terp.erp.employee.lifecycle.LifecycleCatalogService.StatusForm;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;

@Tag(name = "Lifecycle Lists")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/catalogs/{kind}")
@SecurityRequirement(name = "bearerAuth")
public class LifecycleCatalogController {
    private static final String KIND = "warning-types, warning-severities, termination-types, complaint-types, "
            + "resolution-types, expense-types";

    private final LifecycleCatalogService service;

    @Operation(summary = "Entries of a company-managed lifecycle list", description = "keyword matches the name.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"),
            @ApiResponse(responseCode = "404", description = "Unknown list (EMP_050)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CatalogDto>> list(
            @Parameter(description = KIND) @PathVariable String kind,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(ELifecycleCatalogKind.ofPath(kind), companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Active entries for dropdowns")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Unknown list (EMP_050)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(@Parameter(description = KIND) @PathVariable String kind,
                                            @Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId) {
        return ApiMessageDto.ok(service.options(ELifecycleCatalogKind.ofPath(kind), companyId));
    }

    @Operation(summary = "Entry details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such entry in your company (EMP_050)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CatalogDto> get(@Parameter(description = KIND) @PathVariable String kind,
                                         @PathVariable Long id) {
        return ApiMessageDto.ok(service.get(ELifecycleCatalogKind.ofPath(kind), id));
    }

    @Operation(summary = "Add an entry")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken in this list (EMP_011), super admin without "
                    + "companyId (EMP_019)"),
            @ApiResponse(responseCode = "404", description = "Unknown list (EMP_050)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<CatalogDto> create(@Parameter(description = KIND) @PathVariable String kind,
                                            @Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId, @Valid @RequestBody CatalogForm form) {
        return ApiMessageDto.ok(service.create(ELifecycleCatalogKind.ofPath(kind), companyId, form));
    }

    @Operation(summary = "Edit an entry", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken in this list (EMP_011)"),
            @ApiResponse(responseCode = "404", description = "No such entry in your company (EMP_050)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CatalogDto> update(@Parameter(description = KIND) @PathVariable String kind,
                                            @PathVariable Long id, @Valid @RequestBody CatalogForm form) {
        return ApiMessageDto.ok(service.update(ELifecycleCatalogKind.ofPath(kind), id, form));
    }

    @Operation(summary = "Activate / deactivate an entry", description = "Inactive entries stay on existing records "
            + "but cannot be chosen again.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such entry in your company (EMP_050)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<CatalogDto> status(@Parameter(description = KIND) @PathVariable String kind,
                                            @PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(ELifecycleCatalogKind.ofPath(kind), id, form.status()));
    }

    @Operation(summary = "Delete an entry")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Used by a record (EMP_010)"),
            @ApiResponse(responseCode = "404", description = "No such entry in your company (EMP_050)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@Parameter(description = KIND) @PathVariable String kind,
                                      @PathVariable Long id) {
        service.delete(ELifecycleCatalogKind.ofPath(kind), id);
        return ApiMessageDto.ok(null);
    }
}
