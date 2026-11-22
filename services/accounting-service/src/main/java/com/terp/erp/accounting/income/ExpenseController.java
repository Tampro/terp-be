package com.terp.erp.accounting.income;

import com.terp.erp.accounting.income.CashVoucherService.VoucherDto;
import com.terp.erp.accounting.income.CashVoucherService.VoucherForm;
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

import java.time.LocalDate;

import static com.terp.erp.accounting.coa.ECategoryKind.EXPENSE;

@Tag(name = "Expenses")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/expenses")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such expense in your company (ACC_028)";
    private static final String INVALID = "Account inactive or not in the company (ACC_020), invalid field (COMMON)";
    private static final String REFS = "Expense category (ACC_015) or bank account (ACC_006) not in your company";

    private final CashVoucherService service;

    @Operation(summary = "Expenses", description = "Filters categoryId, status, bankAccountId, date range.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('EXPENSE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<VoucherDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) EVoucherStatus status,
            @RequestParam(required = false) Long bankAccountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(EXPENSE, companyId, categoryId, status, bankAccountId, from, to, pageable)));
    }

    @Operation(summary = "Expense details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('EXPENSE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<VoucherDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(EXPENSE, id));
    }

    @Operation(summary = "Record an expense", description = "Starts DRAFT, numbered EXP-YYYY-MM-NNN; accountId null "
            + "= the category's GL account.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = INVALID + ", super admin without companyId (ACC_001)"),
            @ApiResponse(responseCode = "404", description = REFS)})
    @PreAuthorize("@PermissionChecker.has('EXPENSE_MANAGE')")
    @PostMapping
    public ApiMessageDto<VoucherDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody VoucherForm form) {
        return ApiMessageDto.ok(service.create(EXPENSE, companyId, form));
    }

    @Operation(summary = "Edit a draft expense", description = "Full replacement; DRAFT only.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = INVALID + ", not DRAFT (ACC_029)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + "; " + REFS)})
    @PreAuthorize("@PermissionChecker.has('EXPENSE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<VoucherDto> update(@PathVariable Long id, @Valid @RequestBody VoucherForm form) {
        return ApiMessageDto.ok(service.update(EXPENSE, id, form));
    }

    @Operation(summary = "Delete a draft expense", description = "Soft delete; DRAFT only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not DRAFT (ACC_029)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EXPENSE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(EXPENSE, id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Approve an expense", description = "DRAFT → APPROVED; approvedBy = the caller.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not DRAFT (ACC_029)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EXPENSE_APPROVE')")
    @PostMapping("/{id}/approve")
    public ApiMessageDto<VoucherDto> approve(@PathVariable Long id) {
        return ApiMessageDto.ok(service.approve(EXPENSE, id));
    }

    @Operation(summary = "Post an expense", description = "APPROVED → POSTED: bank DEBIT and journal Dr account / "
            + "Cr bank GL.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not APPROVED (ACC_030), bank account inactive "
                    + "(ACC_008), system account unmapped (ACC_017), account unusable (ACC_020)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EXPENSE_APPROVE')")
    @PostMapping("/{id}/post")
    public ApiMessageDto<VoucherDto> post(@PathVariable Long id) {
        return ApiMessageDto.ok(service.post(EXPENSE, id));
    }
}
