package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.ledger.LedgerReportService.AccountBalances;
import com.terp.erp.accounting.ledger.LedgerReportService.CashFlow;
import com.terp.erp.accounting.ledger.LedgerReportService.ExpenseReport;
import com.terp.erp.accounting.ledger.LedgerReportService.GeneralLedger;
import com.terp.erp.accounting.ledger.LedgerReportService.ProfitLoss;
import com.terp.erp.accounting.ledger.LedgerReportService.TrialBalance;
import com.terp.erp.common.ApiMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Double-entry reports (manual 13) with a PDF for each; POSTED and REVERSED entries count, drafts never. */
@Tag(name = "Ledger reports")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/ledger")
@SecurityRequirement(name = "bearerAuth")
public class LedgerReportController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String VIEW = "@PermissionChecker.has('LEDGER_REPORT_VIEW')";
    private static final String PERIOD = " from and to are required, to on or after from, at most 366 days.";
    private static final String BAD = "Bad period (ACC_065), super admin without companyId (ACC_001)";
    private static final String ACCOUNT = "No such ledger account in your company (ACC_010)";

    private final LedgerReportService service;

    @Operation(summary = "Trial balance", description = "Per account with activity: opening, period debit / credit "
            + "and closing (debit or credit column). balanced = totals equal; difference = closing debit - credit."
            + PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/trial-balance")
    public ApiMessageDto<TrialBalance> trialBalance(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.trialBalance(companyId, from, to));
    }

    @Operation(summary = "Trial balance PDF", description = PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/trial-balance/pdf")
    public ResponseEntity<byte[]> trialBalancePdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TrialBalance r = service.trialBalance(companyId, from, to);
        return pdf(LedgerPdf.trialBalance(r), "trial-balance-" + from + "-" + to + ".pdf");
    }

    @Operation(summary = "Profit & loss", description = "Revenue and expense accounts by account type, net profit "
            + "(negative = loss); the year-end closing entry is left out." + PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/profit-loss")
    public ApiMessageDto<ProfitLoss> profitLoss(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.profitLoss(companyId, from, to));
    }

    @Operation(summary = "Profit & loss PDF", description = PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/profit-loss/pdf")
    public ResponseEntity<byte[]> profitLossPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ProfitLoss r = service.profitLoss(companyId, from, to);
        return pdf(LedgerPdf.profitLoss(r), "profit-loss-" + from + "-" + to + ".pdf");
    }

    @Operation(summary = "Cash flow", description = "Direct method over cash / bank accounts (CASH and BANK mapping, "
            + "their children, 111/112/113): Operating / Investing / Financing by the counterpart account code."
            + PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/cash-flow")
    public ApiMessageDto<CashFlow> cashFlow(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.cashFlow(companyId, from, to));
    }

    @Operation(summary = "Cash flow PDF", description = PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/cash-flow/pdf")
    public ResponseEntity<byte[]> cashFlowPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CashFlow r = service.cashFlow(companyId, from, to);
        return pdf(LedgerPdf.cashFlow(r), "cash-flow-" + from + "-" + to + ".pdf");
    }

    @Operation(summary = "General ledger", description = "One account: opening balance, every line with its running "
            + "balance (normal side), closing balance." + PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = ACCOUNT)})
    @PreAuthorize(VIEW)
    @GetMapping("/general-ledger")
    public ApiMessageDto<GeneralLedger> generalLedger(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.generalLedger(accountId, from, to));
    }

    @Operation(summary = "General ledger PDF", description = PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = ACCOUNT)})
    @PreAuthorize(VIEW)
    @GetMapping("/general-ledger/pdf")
    public ResponseEntity<byte[]> generalLedgerPdf(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        GeneralLedger r = service.generalLedger(accountId, from, to);
        return pdf(LedgerPdf.ledger(r, false), "general-ledger-" + r.code() + ".pdf");
    }

    @Operation(summary = "Account statement", description = "General ledger of one account plus, per line, the "
            + "counterpart account codes and the source document (sourceType / sourceId)." + PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = ACCOUNT)})
    @PreAuthorize(VIEW)
    @GetMapping("/account-statement")
    public ApiMessageDto<GeneralLedger> accountStatement(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.accountStatement(accountId, from, to));
    }

    @Operation(summary = "Account statement PDF", description = PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = ACCOUNT)})
    @PreAuthorize(VIEW)
    @GetMapping("/account-statement/pdf")
    public ResponseEntity<byte[]> accountStatementPdf(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        GeneralLedger r = service.accountStatement(accountId, from, to);
        return pdf(LedgerPdf.ledger(r, true), "account-statement-" + r.code() + ".pdf");
    }

    @Operation(summary = "Account balances", description = "Every account's balance (normal side) at the end of asOf "
            + "(default today); hideZero leaves out zero balances. No period limit.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/account-balances")
    public ApiMessageDto<AccountBalances> accountBalances(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue = "false") boolean hideZero) {
        return ApiMessageDto.ok(service.accountBalances(companyId, asOf, hideZero));
    }

    @Operation(summary = "Account balances PDF")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/account-balances/pdf")
    public ResponseEntity<byte[]> accountBalancesPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue = "false") boolean hideZero) {
        AccountBalances r = service.accountBalances(companyId, asOf, hideZero);
        return pdf(LedgerPdf.accountBalances(r), "account-balances-" + r.asOf() + ".pdf");
    }

    @Operation(summary = "Expense report", description = "Expense accounts and their categories (account types) "
            + "with totals; the year-end closing entry is left out." + PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/expense-report")
    public ApiMessageDto<ExpenseReport> expenseReport(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.expenseReport(companyId, from, to));
    }

    @Operation(summary = "Expense report PDF", description = PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = BAD))
    @PreAuthorize(VIEW)
    @GetMapping("/expense-report/pdf")
    public ResponseEntity<byte[]> expenseReportPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ExpenseReport r = service.expenseReport(companyId, from, to);
        return pdf(LedgerPdf.expenseReport(r), "expense-report-" + from + "-" + to + ".pdf");
    }

    static ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}
