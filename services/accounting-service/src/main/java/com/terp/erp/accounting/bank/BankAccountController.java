package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.bank.BankAccountService.BankAccountDto;
import com.terp.erp.accounting.bank.BankAccountService.BankAccountForm;
import com.terp.erp.accounting.bank.BankAccountService.BankTransactionDto;
import com.terp.erp.accounting.bank.BankAccountService.IncomeForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
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

@Tag(name = "Bank accounts")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/bank-accounts")
@SecurityRequirement(name = "bearerAuth")
public class BankAccountController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such bank account in your company (ACC_006)";
    // The POS cashier picks the account a sale is paid into, so POS_SELL may read accounts.
    private static final String READ = "@PermissionChecker.hasAny('BANK_VIEW','POS_SELL')";
    private static final String MANAGE = "@PermissionChecker.has('BANK_MANAGE')";

    private final BankAccountService service;

    @Operation(summary = "Bank accounts of the company", description = "keyword = holder, bank or account "
            + "number; active filters by status (omit for all).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(READ)
    @GetMapping
    public ApiMessageDto<ResponseListDto<BankAccountDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "bankName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, active, keyword, pageable)));
    }

    @Operation(summary = "Bank account details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(READ)
    @GetMapping("/{id}")
    public ApiMessageDto<BankAccountDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a bank account", description = "balance starts at openingBalance (null = 0); "
            + "glAccountId null = the company's BANK mapping (seeds the VAS chart on first use).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Account number already used in the company "
            + "(ACC_007), GL account unusable (ACC_020), BANK unmapped (ACC_017), super admin without companyId "
            + "(ACC_001), invalid field (COMMON)"))
    @PreAuthorize(MANAGE)
    @PostMapping
    public ApiMessageDto<BankAccountDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody BankAccountForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a bank account", description = "Full replacement: omitted optional fields are "
            + "cleared (openingBalance null = 0). Changing openingBalance moves balance by the difference.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Account number already used in the company "
                    + "(ACC_007), invalid field (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public ApiMessageDto<BankAccountDto> update(@PathVariable Long id, @Valid @RequestBody BankAccountForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a bank account", description = "Soft delete; transactions are kept.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Transactions of a bank account")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('BANK_VIEW')")
    @GetMapping("/{id}/transactions")
    public ApiMessageDto<ResponseListDto<BankTransactionDto>> transactions(
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.transactions(id, pageable)));
    }

    @Operation(summary = "Book income on a bank account", description = "Idempotent on (sourceType, sourceId) "
            + "within the company: a repeat returns the first transaction and does not change the balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Bank account inactive (ACC_008), invalid field "
                    + "(COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.hasAny('BANK_MANAGE','POS_SELL')")
    @PostMapping("/{id}/income")
    public ApiMessageDto<BankTransactionDto> income(@PathVariable Long id, @Valid @RequestBody IncomeForm form) {
        return ApiMessageDto.ok(service.income(id, form));
    }
}
