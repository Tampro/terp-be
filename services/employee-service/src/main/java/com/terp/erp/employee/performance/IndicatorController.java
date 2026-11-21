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
import com.terp.erp.employee.performance.IndicatorService.IndicatorDto;
import com.terp.erp.employee.performance.IndicatorService.IndicatorForm;
import com.terp.erp.employee.performance.PerformanceCatalogService.StatusForm;
import com.terp.erp.employee.performance.PerformanceSupport.StatusCounts;

import static com.terp.erp.employee.performance.PerformanceSupport.COMPANY;

@Tag(name = "Indicators")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/indicators")
@SecurityRequirement(name = "bearerAuth")
public class IndicatorController {
    private static final String NOT_FOUND = "No such indicator in your company (EMP_072)";

    private final IndicatorService service;

    @Operation(summary = "Indicators of the company", description = "keyword matches the name or description; "
            + "categoryId = the Category filter; status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<IndicatorDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, categoryId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, categoryId));
    }

    @Operation(summary = "Indicator details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<IndicatorDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an indicator")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), category not in the "
            + "company's list (EMP_071) or inactive (EMP_054), super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<IndicatorDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                              Long companyId, @Valid @RequestBody IndicatorForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an indicator", description = "Full replacement; weight omitted = 1, status omitted = "
            + "kept. Past reviews keep the values they were rated with.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_011, EMP_071, EMP_054"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<IndicatorDto> update(@PathVariable Long id, @Valid @RequestBody IndicatorForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Toggle Status", description = "Only active indicators appear on new reviews.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<IndicatorDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an indicator", description = "Past reviews keep their ratings.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
