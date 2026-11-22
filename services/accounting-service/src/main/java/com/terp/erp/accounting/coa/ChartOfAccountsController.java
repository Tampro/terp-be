package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.coa.ChartOfAccountsService.AccountDto;
import com.terp.erp.accounting.coa.ChartOfAccountsService.AccountForm;
import com.terp.erp.accounting.coa.ChartOfAccountsService.AccountTypeDto;
import com.terp.erp.accounting.coa.ChartOfAccountsService.AccountTypeForm;
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

@Tag(name = "Chart of accounts")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting")
@SecurityRequirement(name = "bearerAuth")
public class ChartOfAccountsController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String VIEW = "@PermissionChecker.has('COA_VIEW')";
    private static final String MANAGE = "@PermissionChecker.has('COA_MANAGE')";
    private static final String TYPE_NOT_FOUND = "No such account type in your company (ACC_009)";
    private static final String ACCOUNT_NOT_FOUND = "No such ledger account in your company (ACC_010)";

    private final ChartOfAccountsService service;

    @Operation(summary = "Account types", description = "The first call seeds the default VAS TT200 chart.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/account-types")
    public ApiMessageDto<ResponseListDto<AccountTypeDto>> types(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) EAccountCategory category,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 50, sort = "code", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.searchTypes(companyId, category, active, pageable)));
    }

    @Operation(summary = "Account type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/account-types/{id}")
    public ApiMessageDto<AccountTypeDto> type(@PathVariable Long id) {
        return ApiMessageDto.ok(service.getType(id));
    }

    @Operation(summary = "Add an account type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code already used (ACC_031), super admin "
            + "without companyId (ACC_001), invalid field (COMMON)"))
    @PreAuthorize(MANAGE)
    @PostMapping("/account-types")
    public ApiMessageDto<AccountTypeDto> createType(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody AccountTypeForm form) {
        return ApiMessageDto.ok(service.createType(companyId, form));
    }

    @Operation(summary = "Edit an account type", description = "Full replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Code already used (ACC_031), invalid field (COMMON)"),
            @ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/account-types/{id}")
    public ApiMessageDto<AccountTypeDto> updateType(@PathVariable Long id,
                                                    @Valid @RequestBody AccountTypeForm form) {
        return ApiMessageDto.ok(service.updateType(id, form));
    }

    @Operation(summary = "Delete an account type", description = "Refused while accounts use it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Used by accounts (ACC_032)"),
            @ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @DeleteMapping("/account-types/{id}")
    public ApiMessageDto<Void> deleteType(@PathVariable Long id) {
        service.deleteType(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Ledger accounts (chart of accounts)", description = "keyword = code or name; filters "
            + "accountTypeId, normalBalance, active (omit for all). The first call seeds the default VAS TT200 chart.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/chart-of-accounts")
    public ApiMessageDto<ResponseListDto<AccountDto>> accounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long accountTypeId,
            @RequestParam(required = false) ENormalBalance normalBalance,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 100, sort = "code", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.searchAccounts(companyId, accountTypeId, normalBalance, active, keyword, pageable)));
    }

    @Operation(summary = "Ledger account details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ACCOUNT_NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/chart-of-accounts/{id}")
    public ApiMessageDto<AccountDto> account(@PathVariable Long id) {
        return ApiMessageDto.ok(service.getAccount(id));
    }

    @Operation(summary = "Add a ledger account", description = "currentBalance starts at openingBalance (null = 0); "
            + "normalBalance null = the type's.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unknown account type (ACC_009), code already "
            + "used (ACC_011), invalid parent (ACC_012), super admin without companyId (ACC_001), invalid field "
            + "(COMMON)"))
    @PreAuthorize(MANAGE)
    @PostMapping("/chart-of-accounts")
    public ApiMessageDto<AccountDto> createAccount(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody AccountForm form) {
        return ApiMessageDto.ok(service.createAccount(companyId, form));
    }

    @Operation(summary = "Edit a ledger account", description = "Full replacement: omitted optional fields are "
            + "cleared. A new openingBalance moves currentBalance by the difference.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unknown account type (ACC_009), code already used "
                    + "(ACC_011), invalid parent (ACC_012), invalid field (COMMON)"),
            @ApiResponse(responseCode = "404", description = ACCOUNT_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/chart-of-accounts/{id}")
    public ApiMessageDto<AccountDto> updateAccount(@PathVariable Long id, @Valid @RequestBody AccountForm form) {
        return ApiMessageDto.ok(service.updateAccount(id, form));
    }

    @Operation(summary = "Delete a ledger account", description = "Refused while it has journal lines, child "
            + "accounts, a system mapping, a category or a bank account.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Account in use (ACC_013)"),
            @ApiResponse(responseCode = "404", description = ACCOUNT_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @DeleteMapping("/chart-of-accounts/{id}")
    public ApiMessageDto<Void> deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id);
        return ApiMessageDto.ok(null);
    }
}
