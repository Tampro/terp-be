package com.terp.erp.payroll.salary;

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
import com.terp.erp.payroll.salary.EmployeeSalaryService.SalaryDto;
import com.terp.erp.payroll.salary.EmployeeSalaryService.SalaryForm;
import com.terp.erp.payroll.salary.EmployeeSalaryService.StatusCounts;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Employee salaries")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll/salaries")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeSalaryController {
    private static final String KEYWORD = "Matches the employee's name, no. or email";
    private static final String NOT_FOUND = "No such salary in your company (PAY_007)";
    private static final String FORM_ERRORS = "Employee already has an active salary (PAY_008), component not in the "
            + "company (PAY_002), inactive (PAY_009), listed twice (PAY_010), percentage over 100 (PAY_004)";

    private final EmployeeSalaryService service;

    public record StatusForm(@NotNull EmployeeSalary.EStatus status) {
    }

    @Operation(summary = "Employee salaries of the company", description = "employeeId = the Employee filter; "
            + "status = the tab. Needs EMPLOYEE_VIEW too (names and photos come from employee-service).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (PAY_006)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<SalaryDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) EmployeeSalary.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, employeeId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId));
    }

    @Operation(summary = "Salary details", description = "structure = the monthly amounts: each component (a "
            + "percentage is of the basic salary, rounded to whole units), total earnings (basic + earnings), total "
            + "deductions and net.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<SalaryDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a salary structure", description = "The employee is checked in employee-service as you.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (PAY_006)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping
    public ApiMessageDto<SalaryDto> create(@Valid @RequestBody SalaryForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a salary structure", description = "Full replacement (components = full set); status "
            + "omitted = kept. An inactive component already in the structure may stay.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such salary (PAY_007) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<SalaryDto> update(@PathVariable Long id, @Valid @RequestBody SalaryForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Toggle Status")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Another active salary of the employee (PAY_008)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<SalaryDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a salary structure")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
