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
import com.terp.erp.employee.performance.GoalService.GoalDto;
import com.terp.erp.employee.performance.GoalService.GoalForm;
import com.terp.erp.employee.performance.GoalService.ProgressForm;
import com.terp.erp.employee.performance.GoalService.StatusCounts;

import static com.terp.erp.employee.performance.PerformanceSupport.COMPANY;

@Tag(name = "Employee goals")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/goals")
@SecurityRequirement(name = "bearerAuth")
public class GoalController {
    private static final String KEYWORD = "Matches the title, the target or the employee's name, email or no.";
    private static final String NOT_FOUND = "No such goal in your company (EMP_074)";

    private final GoalService service;

    @Operation(summary = "Goals of the company", description = "employeeId / goalTypeId = the filters; status = "
            + "the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<GoalDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long goalTypeId,
            @RequestParam(required = false) EmployeeGoal.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, employeeId, goalTypeId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Not Started, In Progress, Completed)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long goalTypeId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId, goalTypeId));
    }

    @Operation(summary = "Goal details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<GoalDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a goal")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Employee terminated (EMP_058), goal type not in the "
                    + "company's list (EMP_071) or inactive (EMP_054), end before start (EMP_052)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<GoalDto> create(@Valid @RequestBody GoalForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a goal", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_058, EMP_071, EMP_054, EMP_052"),
            @ApiResponse(responseCode = "404", description = "No such goal (EMP_074) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<GoalDto> update(@PathVariable Long id, @Valid @RequestBody GoalForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Progress", description = "100 completes the goal; above 0 starts a not-started one; "
            + "below 100 reopens a completed one as in progress.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}/progress")
    public ApiMessageDto<GoalDto> progress(@PathVariable Long id, @Valid @RequestBody ProgressForm form) {
        return ApiMessageDto.ok(service.updateProgress(id, form.progress()));
    }

    @Operation(summary = "Delete a goal")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
