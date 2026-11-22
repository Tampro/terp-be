package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.bank.BankAccountService.BankTransactionDto;
import com.terp.erp.accounting.bank.BankTransferService.TransferDto;
import com.terp.erp.accounting.bank.BankTransferService.TransferForm;
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

@Tag(name = "Bank transfers and transactions")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting")
@SecurityRequirement(name = "bearerAuth")
public class BankTransferController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String VIEW = "@PermissionChecker.has('BANK_VIEW')";
    private static final String MANAGE = "@PermissionChecker.has('BANK_MANAGE')";
    private static final String NOT_FOUND = "No such bank transfer in your company (ACC_024)";
    private static final String TX_NOT_FOUND = "No such bank transaction in your company (ACC_022)";
    private static final String INVALID = "Same account on both sides (ACC_025), invalid field (COMMON)";

    private final BankTransferService transfers;
    private final BankAccountService accounts;

    @Operation(summary = "Bank transaction details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = TX_NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/bank-transactions/{id}")
    public ApiMessageDto<BankTransactionDto> transaction(@PathVariable Long id) {
        return ApiMessageDto.ok(accounts.getTransaction(id));
    }

    @Operation(summary = "Reconcile a bank transaction", description = "CLEARED → RECONCILED.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not CLEARED (ACC_023)"),
            @ApiResponse(responseCode = "404", description = TX_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/bank-transactions/{id}/reconcile")
    public ApiMessageDto<BankTransactionDto> reconcile(@PathVariable Long id) {
        return ApiMessageDto.ok(accounts.reconcile(id));
    }

    @Operation(summary = "Bank transfers", description = "Filters status, bankAccountId (either side), date range.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/bank-transfers")
    public ApiMessageDto<ResponseListDto<TransferDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ETransferStatus status,
            @RequestParam(required = false) Long bankAccountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                transfers.search(companyId, status, bankAccountId, from, to, pageable)));
    }

    @Operation(summary = "Bank transfer details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/bank-transfers/{id}")
    public ApiMessageDto<TransferDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(transfers.get(id));
    }

    @Operation(summary = "Create a bank transfer", description = "Starts PENDING; numbered TRF-YYYY-MM-NNN.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = INVALID + ", super admin without companyId (ACC_001)"),
            @ApiResponse(responseCode = "404", description = "A bank account not in your company (ACC_006)")})
    @PreAuthorize(MANAGE)
    @PostMapping("/bank-transfers")
    public ApiMessageDto<TransferDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody TransferForm form) {
        return ApiMessageDto.ok(transfers.create(companyId, form));
    }

    @Operation(summary = "Edit a pending bank transfer", description = "Full replacement; PENDING only.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = INVALID + ", not PENDING (ACC_026)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + " or bank account (ACC_006)")})
    @PreAuthorize(MANAGE)
    @PutMapping("/bank-transfers/{id}")
    public ApiMessageDto<TransferDto> update(@PathVariable Long id, @Valid @RequestBody TransferForm form) {
        return ApiMessageDto.ok(transfers.update(id, form));
    }

    @Operation(summary = "Delete a pending bank transfer", description = "Soft delete; PENDING only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not PENDING (ACC_026)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @DeleteMapping("/bank-transfers/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        transfers.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Process a bank transfer", description = "PENDING → COMPLETED: DEBIT amount + charges on "
            + "the source, CREDIT amount on the destination, journal Dr destination GL / Dr BANK_CHARGES / Cr source "
            + "GL.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not PENDING (ACC_026), account inactive (ACC_008), "
                    + "system account unmapped (ACC_017), GL account unusable (ACC_020)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/bank-transfers/{id}/process")
    public ApiMessageDto<TransferDto> process(@PathVariable Long id) {
        return ApiMessageDto.ok(transfers.process(id));
    }
}
