package com.terp.erp.employee.training;

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
import org.springframework.format.annotation.DateTimeFormat;
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
import com.terp.erp.employee.training.EmployeeTrainingService.BulkForm;
import com.terp.erp.employee.training.EmployeeTrainingService.BulkResult;
import com.terp.erp.employee.training.EmployeeTrainingService.Dashboard;
import com.terp.erp.employee.training.EmployeeTrainingService.StatusCounts;
import com.terp.erp.employee.training.EmployeeTrainingService.TrainingDto;
import com.terp.erp.employee.training.EmployeeTrainingService.TrainingForm;

import java.time.LocalDate;

import static com.terp.erp.employee.training.TrainingSupport.COMPANY;

@Tag(name = "Employee trainings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training/employee-trainings")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeTrainingController {
    private static final String KEYWORD = "Matches the employee's name, email or no., or the program name";
    private static final String NOT_FOUND = "No such employee training in your company (EMP_082)";
    private static final String ASSIGN_ERRORS = "Employee terminated (EMP_058), program not in the company "
            + "(EMP_081), completed or cancelled (EMP_083), full (EMP_084), already assigned (EMP_085), completion "
            + "before assignment (EMP_052), certificate not in the media library (EMP_012)";

    private final EmployeeTrainingService service;

    @Operation(summary = "Employee trainings of the company", description = "programId = the Training Program "
            + "filter; from / to = Assigned From / To; status = the tab; result = PASSED / FAILED from passed.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TrainingDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EmployeeTraining.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "assignedDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, programId, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Assigned, In Progress, Completed, Failed)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, programId, from, to));
    }

    @Operation(summary = "Training Dashboard", description = "Summary cards, completion rate per program, the 5 most "
            + "recent completions and the next 5 assigned trainings (assigned date from today).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/dashboard")
    public ApiMessageDto<Dashboard> dashboard(@Parameter(description = COMPANY) @RequestParam(required = false)
                                              Long companyId) {
        return ApiMessageDto.ok(service.dashboard(companyId));
    }

    @Operation(summary = "Training details", description = "Training information, employee details, program "
            + "details and a signed certificate link.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TrainingDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Assign Training", description = "Assigned By = your employee profile, when you have one.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ASSIGN_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping
    public ApiMessageDto<TrainingDto> assign(@Valid @RequestBody TrainingForm form) {
        return ApiMessageDto.ok(service.assign(form));
    }

    @Operation(summary = "Bulk Assign", description = "Creates ASSIGNED trainings; employees already assigned to "
            + "the program are skipped. All or nothing otherwise.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_058, EMP_081, EMP_083, not enough seats (EMP_084)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping("/bulk")
    public ApiMessageDto<BulkResult> bulk(@Valid @RequestBody BulkForm form) {
        return ApiMessageDto.ok(service.bulkAssign(form));
    }

    @Operation(summary = "Edit a training", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ASSIGN_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such training (EMP_082) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TrainingDto> update(@PathVariable Long id, @Valid @RequestBody TrainingForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a training")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
