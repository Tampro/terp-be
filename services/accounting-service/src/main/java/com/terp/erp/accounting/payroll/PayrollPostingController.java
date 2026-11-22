package com.terp.erp.accounting.payroll;

import com.terp.erp.accounting.payroll.PayrollPostingService.PayrollPostingDto;
import com.terp.erp.accounting.payroll.PayrollPostingService.PayrollPostingForm;
import com.terp.erp.common.ApiMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payroll postings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/payroll-postings")
@SecurityRequirement(name = "bearerAuth")
public class PayrollPostingController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final PayrollPostingService service;

    @Operation(summary = "Book a payroll payment", description = "Called by payroll-service when a run is paid. One "
            + "bank DEBIT of amount and one journal entry Dr SALARY_PAYABLE (334) / Cr the bank's GL account, source "
            + "PAYROLL_RUN / runId, preceded by the salary expense accrual Dr SALARY_EXPENSE (6421) / Cr "
            + "SALARY_PAYABLE (334) of the same amount, source PAYROLL_ACCRUAL / runId, dated accrualDate. Idempotent per run: a repeat "
            + "returns the first booking and moves no balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Inactive bank account (ACC_008), SALARY_PAYABLE, "
                    + "SALARY_EXPENSE or BANK unmapped (ACC_017), date in a closed year (ACC_070), super admin without companyId "
                    + "(ACC_001)"),
            @ApiResponse(responseCode = "404", description = "No such bank account in your company (ACC_006)")})
    @PreAuthorize("@PermissionChecker.hasAny('BANK_MANAGE','PAYROLL_PAY')")
    @PostMapping
    public ApiMessageDto<PayrollPostingDto> post(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody PayrollPostingForm form) {
        return ApiMessageDto.ok(service.post(companyId, form));
    }
}
