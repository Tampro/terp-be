package com.terp.erp.payroll.run;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.payroll.run.PayrollCalculationService.Calculation;
import com.terp.erp.payroll.run.PayrollCalculationService.RunOption;

import java.util.List;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Show Payroll")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll/entries")
@SecurityRequirement(name = "bearerAuth")
public class PayrollCalculationController {
    private final PayrollCalculationService service;

    @Operation(summary = "Payroll runs of an employee (the run dropdown)", description = "One option per run the "
            + "employee has an entry in, newest pay period first.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping
    public ApiMessageDto<List<RunOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam Long employeeId) {
        return ApiMessageDto.ok(service.options(companyId, employeeId));
    }

    @Operation(summary = "Payroll Calculation of an entry", description = "run = header and period; entry = basic "
            + "salary, earnings and deduction components, LOP / unpaid leave / overtime and the final calculation as "
            + "processed; records = Daily Attendance Records of the period, read now from attendance-service as you "
            + "(needs ATTENDANCE_VIEW and EMPLOYEE_VIEW too).")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "No such entry in your company (PAY_018)"),
            @ApiResponse(responseCode = "503", description = "attendance-service (PAY_011) or employee-service "
                    + "(PAY_006) unavailable")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}/calculation")
    public ApiMessageDto<Calculation> calculation(@PathVariable Long id) {
        return ApiMessageDto.ok(service.calculation(id));
    }
}
