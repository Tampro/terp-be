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
import com.terp.erp.employee.performance.PerformanceCatalogService.StatusForm;
import com.terp.erp.employee.performance.PerformanceSupport.Ref;
import com.terp.erp.employee.performance.ReviewCycleService.CycleDto;
import com.terp.erp.employee.performance.ReviewCycleService.CycleForm;

import java.util.List;

import static com.terp.erp.employee.performance.PerformanceSupport.COMPANY;

@Tag(name = "Review cycles")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/review-cycles")
@SecurityRequirement(name = "bearerAuth")
public class ReviewCycleController {
    private static final String NOT_FOUND = "No such review cycle in your company (EMP_073)";

    private final ReviewCycleService service;

    @Operation(summary = "Review cycles of the company", description = "keyword matches the name or description; "
            + "frequency / status = the All Frequencies / All Statuses filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CycleDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReviewCycle.EFrequency frequency,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, frequency, status, pageable)));
    }

    @Operation(summary = "Active cycles for dropdowns, by name")
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(@Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Review cycle details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CycleDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a review cycle")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), super admin without "
            + "companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<CycleDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                          Long companyId, @Valid @RequestBody CycleForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a review cycle", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CycleDto> update(@PathVariable Long id, @Valid @RequestBody CycleForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate / deactivate a review cycle", description = "Inactive cycles cannot be chosen for "
            + "new reviews.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<CycleDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a review cycle")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
