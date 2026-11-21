package com.terp.erp.payroll.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.dashboard.PayrollDashboardService.PayrollDashboard;

@Tag(name = "Payroll dashboard")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll")
@SecurityRequirement(name = "bearerAuth")
public class PayrollDashboardController {
    private final PayrollDashboardService service;

    @Operation(summary = "Payroll figures of the company dashboard",
            description = "Net pay of COMPLETED runs by pay date: this month, last month and the 12 months of year "
                    + "(default: the current year).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/dashboard")
    public ApiMessageDto<PayrollDashboard> dashboard(
            @Parameter(description = Texts.COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(service.dashboard(companyId, year));
    }
}
