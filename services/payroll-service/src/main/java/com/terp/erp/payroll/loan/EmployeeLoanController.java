package com.terp.erp.payroll.loan;

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
import com.terp.erp.payroll.loan.EmployeeLoanService.LoanDto;
import com.terp.erp.payroll.loan.EmployeeLoanService.LoanForm;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Employee loans")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeLoanController {
    private static final String NOT_FOUND = "No such loan in your company (PAY_029)";
    private static final String FORM_ERRORS = "End before start (PAY_031), neither installment nor number of "
            + "installments (PAY_030), loan type not an active LOAN type of the company (PAY_032)";

    private final EmployeeLoanService service;

    @Operation(summary = "Loans of an employee")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/employees/{employeeId}/loans")
    public ApiMessageDto<ResponseListDto<LoanDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @PathVariable Long employeeId,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, employeeId, pageable)));
    }

    @Operation(summary = "Add a loan", description = "ACTIVE with remaining balance = amount. Each payroll run whose "
            + "period overlaps [startDate, endDate] deducts the installment, capped at the remaining balance. Needs "
            + "EMPLOYEE_VIEW too (the employee is checked in employee-service).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", super admin without companyId "
                    + "(PAY_001)"),
            @ApiResponse(responseCode = "404", description = "Employee not in your company (PAY_029 / EMP_xxx)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (PAY_006)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping("/employees/{employeeId}/loans")
    public ApiMessageDto<LoanDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @PathVariable Long employeeId, @Valid @RequestBody LoanForm form) {
        return ApiMessageDto.ok(service.create(companyId, employeeId, form));
    }

    @Operation(summary = "Loan details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/loans/{id}")
    public ApiMessageDto<LoanDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Edit a loan", description = "Full replacement. remainingBalance = amount − what processed "
            + "runs deducted; 0 → COMPLETED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", amount below what was repaid "
                    + "(PAY_035)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/loans/{id}")
    public ApiMessageDto<LoanDto> update(@PathVariable Long id, @Valid @RequestBody LoanForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a loan", description = "Only a loan no payroll run has deducted from.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already repaid in a run (PAY_035)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/loans/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
