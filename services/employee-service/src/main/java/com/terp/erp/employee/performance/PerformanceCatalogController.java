package com.terp.erp.employee.performance;

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
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.performance.PerformanceCatalogService.CatalogDto;
import com.terp.erp.employee.performance.PerformanceCatalogService.CatalogForm;
import com.terp.erp.employee.performance.PerformanceCatalogService.StatusForm;
import com.terp.erp.employee.performance.PerformanceSupport.Ref;
import com.terp.erp.employee.performance.PerformanceSupport.StatusCounts;

import java.util.List;

import static com.terp.erp.employee.performance.PerformanceSupport.COMPANY;

@Tag(name = "Goal types & indicator categories")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/{kind:" + EPerformanceCatalogKind.PATHS + "}")
@SecurityRequirement(name = "bearerAuth")
public class PerformanceCatalogController {
    private static final String KIND = "goal-types or indicator-categories";
    private static final String NOT_FOUND = "No such entry in your company (EMP_071)";

    private final PerformanceCatalogService service;

    @Operation(summary = "Goal types / indicator categories", description = "keyword matches the name or "
            + "description; status = the All / Active / Inactive filter.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CatalogDto>> list(
            @Parameter(description = KIND) @PathVariable String kind,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(EPerformanceCatalogKind.ofPath(kind), companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = KIND) @PathVariable String kind,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(EPerformanceCatalogKind.ofPath(kind), companyId, keyword));
    }

    @Operation(summary = "Active entries for dropdowns, by name")
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(@Parameter(description = KIND) @PathVariable String kind,
                                            @Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId) {
        return ApiMessageDto.ok(service.options(EPerformanceCatalogKind.ofPath(kind), companyId));
    }

    @Operation(summary = "Entry details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CatalogDto> get(@Parameter(description = KIND) @PathVariable String kind,
                                         @PathVariable Long id) {
        return ApiMessageDto.ok(service.get(EPerformanceCatalogKind.ofPath(kind), id));
    }

    @Operation(summary = "Add an entry")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken in this list (EMP_011), super admin "
            + "without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<CatalogDto> create(@Parameter(description = KIND) @PathVariable String kind,
                                            @Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId, @Valid @RequestBody CatalogForm form) {
        return ApiMessageDto.ok(service.create(EPerformanceCatalogKind.ofPath(kind), companyId, form));
    }

    @Operation(summary = "Edit an entry", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken in this list (EMP_011)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CatalogDto> update(@Parameter(description = KIND) @PathVariable String kind,
                                            @PathVariable Long id, @Valid @RequestBody CatalogForm form) {
        return ApiMessageDto.ok(service.update(EPerformanceCatalogKind.ofPath(kind), id, form));
    }

    @Operation(summary = "Toggle Status", description = "Inactive entries stay on existing records but cannot be "
            + "chosen again.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<CatalogDto> status(@Parameter(description = KIND) @PathVariable String kind,
                                            @PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(EPerformanceCatalogKind.ofPath(kind), id, form.status()));
    }

    @Operation(summary = "Delete an entry")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Used by an indicator or goal (EMP_010)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@Parameter(description = KIND) @PathVariable String kind,
                                      @PathVariable Long id) {
        service.delete(EPerformanceCatalogKind.ofPath(kind), id);
        return ApiMessageDto.ok(null);
    }
}
