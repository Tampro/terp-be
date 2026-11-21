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
import com.terp.erp.employee.lifecycle.WarningService.PlanForm;
import com.terp.erp.employee.lifecycle.WarningService.StatusCounts;
import com.terp.erp.employee.lifecycle.WarningService.StatusForm;
import com.terp.erp.employee.lifecycle.WarningService.WarningDto;
import com.terp.erp.employee.lifecycle.WarningService.WarningForm;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;

@Tag(name = "Warnings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/warnings")
@SecurityRequirement(name = "bearerAuth")
public class WarningController {
    private static final String KEYWORD = "Matches the employee's name, email or employee no., or the subject";

    private final WarningService service;

    @Operation(summary = "Warnings of the company", description = "typeId / severityId = the Type and Severity "
            + "filters (entries of /catalogs/warning-types and /catalogs/warning-severities); status = the tab. "
            + "expired = past the expiry date while Issued / Acknowledged.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<WarningDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long severityId,
            @RequestParam(required = false) EmployeeWarning.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "warningDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, employeeId, typeId, severityId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Draft, Issued, Acknowledged, Expired)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) Long severityId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId, typeId, severityId));
    }

    @Operation(summary = "Warning details", description = "With the improvement plan and a signed document link.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such warning in your company (EMP_051)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<WarningDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a warning (status DRAFT)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Warned by themselves (EMP_053), type / severity not in "
                    + "the company's lists (EMP_050) or inactive (EMP_054), expiry or plan end before its start "
                    + "(EMP_052), file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<WarningDto> create(@Valid @RequestBody WarningForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a warning", description = "Full replacement of the form; status, acknowledgment and "
            + "plan progress are kept (turning the plan off clears it).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_050, EMP_052, EMP_053, EMP_054, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such warning (EMP_051) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<WarningDto> update(@PathVariable Long id, @Valid @RequestBody WarningForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Change Status (Draft, Issued, Acknowledged, Expired)",
            description = "Acknowledgment date (today when acknowledging without one) and employee response are "
                    + "replaced by what is sent.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Acknowledged before the warning date (EMP_052)"),
            @ApiResponse(responseCode = "404", description = "No such warning in your company (EMP_051)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<WarningDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form));
    }

    @Operation(summary = "Improvement Plan (create, update or turn off)", description = "Full replacement of the plan.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "End before start (EMP_052)"),
            @ApiResponse(responseCode = "404", description = "No such warning in your company (EMP_051)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/improvement-plan")
    public ApiMessageDto<WarningDto> plan(@PathVariable Long id, @Valid @RequestBody PlanForm form) {
        return ApiMessageDto.ok(service.updatePlan(id, form));
    }

    @Operation(summary = "Delete a warning")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such warning in your company (EMP_051)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
