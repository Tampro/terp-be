package com.terp.erp.accounting.report;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.accounting.report.ReportService.AgingReport;
import com.terp.erp.accounting.report.ReportService.BalanceRow;
import com.terp.erp.accounting.report.ReportService.EParty;
import com.terp.erp.accounting.report.ReportService.PartyReport;
import com.terp.erp.accounting.report.ReportService.TaxSummary;
import com.terp.erp.common.ApiMessageDto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Accounting reports")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String VIEW = "@PermissionChecker.has('ACCOUNTING_REPORT_VIEW')";
    private static final String COMMERCE = " Invoices and returns are read from commerce-service with your token "
            + "(SALES_VIEW / PURCHASE_VIEW needed there).";
    private static final String AGING = "Open (posted / partly paid) invoices dated on or before asOf (default "
            + "today), per party, by days past the due date: current, 1-30, 31-60, 61-90, over 90." + COMMERCE;
    private static final String PERIOD = "from and to are required, to on or after from, at most 366 days.";
    private static final String NO_COMPANY = "Super admin without companyId (ACC_001)";
    private static final String DOWN = "Commerce unavailable (ACC_060)";

    private final ReportService service;
    private final AccountDashboardService dashboard;

    @Operation(summary = "Account dashboard",
            description = "Money of the calendar year (default this year): POSTED revenue / expense vouchers by voucher "
                    + "date, CLEARED customer / vendor payments by payment date; netCashFlow = (totalRevenue + "
                    + "customerPayments) - (totalExpense + vendorPayments); monthly in / out; clients and vendors = "
                    + "current non-deleted counts; the 5 latest revenues and expenses of any status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize(VIEW)
    @GetMapping("/dashboard")
    public ApiMessageDto<AccountDashboardService.AccountDashboard> dashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = "Default this year") @RequestParam(required = false) Integer year) {
        return ApiMessageDto.ok(dashboard.dashboard(companyId, year));
    }

    @Operation(summary = "Invoice aging (customers)", description = AGING)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Customer not in your company (ACC_002)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/invoice-aging")
    public ApiMessageDto<AgingReport> invoiceAging(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long customerId) {
        return ApiMessageDto.ok(service.aging(EParty.CUSTOMER, companyId, asOf, customerId));
    }

    @Operation(summary = "Invoice aging PDF", description = AGING)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Customer not in your company (ACC_002)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/invoice-aging/pdf")
    public ResponseEntity<byte[]> invoiceAgingPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long customerId) {
        AgingReport r = service.aging(EParty.CUSTOMER, companyId, asOf, customerId);
        return pdf(ReportPdf.aging(r), "invoice-aging-" + r.asOf() + ".pdf");
    }

    @Operation(summary = "Bill aging (vendors)", description = AGING)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Vendor not in your company (ACC_004)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/bill-aging")
    public ApiMessageDto<AgingReport> billAging(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long vendorId) {
        return ApiMessageDto.ok(service.aging(EParty.VENDOR, companyId, asOf, vendorId));
    }

    @Operation(summary = "Bill aging PDF", description = AGING)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "Vendor not in your company (ACC_004)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/bill-aging/pdf")
    public ResponseEntity<byte[]> billAgingPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long vendorId) {
        AgingReport r = service.aging(EParty.VENDOR, companyId, asOf, vendorId);
        return pdf(ReportPdf.aging(r), "bill-aging-" + r.asOf() + ".pdf");
    }

    @Operation(summary = "Tax summary", description = "Output tax of issued sales invoices and input tax of issued "
            + "purchase invoices dated in the period, less approved credit / debit note tax; net liability = net "
            + "output - net input. " + PERIOD + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065), " + NO_COMPANY),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/tax-summary")
    public ApiMessageDto<TaxSummary> taxSummary(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.taxSummary(companyId, from, to));
    }

    @Operation(summary = "Tax summary PDF", description = "Same figures and filters as GET /tax-summary. " + PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065), " + NO_COMPANY),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/tax-summary/pdf")
    public ResponseEntity<byte[]> taxSummaryPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        TaxSummary r = service.taxSummary(companyId, from, to);
        return pdf(ReportPdf.taxSummary(r), "tax-summary-" + r.from() + "-" + r.to() + ".pdf");
    }

    @Operation(summary = "Customer balances", description = "Per customer: open invoice balances - unapplied credit "
            + "notes. showZero=false (default) leaves out customers with nothing open." + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/customer-balances")
    public ApiMessageDto<List<BalanceRow>> customerBalances(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "false") boolean showZero) {
        return ApiMessageDto.ok(service.balances(EParty.CUSTOMER, companyId, showZero));
    }

    @Operation(summary = "Customer balances PDF", description = "Same rows and filters as GET /customer-balances, "
            + "with a total line." + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/customer-balances/pdf")
    public ResponseEntity<byte[]> customerBalancesPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "false") boolean showZero) {
        return pdf(ReportPdf.balances(EParty.CUSTOMER, service.balances(EParty.CUSTOMER, companyId, showZero)),
                "customer-balances.pdf");
    }

    @Operation(summary = "Vendor balances", description = "Per vendor: open bill balances - unapplied debit notes. "
            + "showZero=false (default) leaves out vendors with nothing open." + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/vendor-balances")
    public ApiMessageDto<List<BalanceRow>> vendorBalances(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "false") boolean showZero) {
        return ApiMessageDto.ok(service.balances(EParty.VENDOR, companyId, showZero));
    }

    @Operation(summary = "Vendor balances PDF", description = "Same rows and filters as GET /vendor-balances, with a "
            + "total line." + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/vendor-balances/pdf")
    public ResponseEntity<byte[]> vendorBalancesPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "false") boolean showZero) {
        return pdf(ReportPdf.balances(EParty.VENDOR, service.balances(EParty.VENDOR, companyId, showZero)),
                "vendor-balances.pdf");
    }

    @Operation(summary = "Customer report", description = "Invoiced, returns, credit notes and payments in the period, "
            + "and today's balance. " + PERIOD + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = "Customer not in your company (ACC_002)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/customers/{customerId}")
    public ApiMessageDto<PartyReport> customer(@PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.partyReport(EParty.CUSTOMER, customerId, from, to));
    }

    @Operation(summary = "Customer report PDF", description = PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = "Customer not in your company (ACC_002)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/customers/{customerId}/pdf")
    public ResponseEntity<byte[]> customerPdf(@PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PartyReport r = service.partyReport(EParty.CUSTOMER, customerId, from, to);
        return pdf(ReportPdf.party(r), "customer-" + r.code() + ".pdf");
    }

    @Operation(summary = "Vendor report", description = "Billed, returns, debit notes and payments in the period, and "
            + "today's balance. " + PERIOD + COMMERCE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = "Vendor not in your company (ACC_004)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/vendors/{vendorId}")
    public ApiMessageDto<PartyReport> vendor(@PathVariable Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.partyReport(EParty.VENDOR, vendorId, from, to));
    }

    @Operation(summary = "Vendor report PDF", description = PERIOD)
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Bad period (ACC_065)"),
            @ApiResponse(responseCode = "404", description = "Vendor not in your company (ACC_004)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize(VIEW)
    @GetMapping("/vendors/{vendorId}/pdf")
    public ResponseEntity<byte[]> vendorPdf(@PathVariable Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PartyReport r = service.partyReport(EParty.VENDOR, vendorId, from, to);
        return pdf(ReportPdf.party(r), "vendor-" + r.code() + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}
