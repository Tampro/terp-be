package com.terp.erp.commerce.pos;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.commerce.pos.PosReportService.CustomerReport;
import com.terp.erp.commerce.pos.PosReportService.ProductReport;
import com.terp.erp.commerce.pos.PosReportService.SalesReport;
import com.terp.erp.commerce.pos.PosService.BarcodeForm;
import com.terp.erp.commerce.pos.PosService.CartForm;
import com.terp.erp.commerce.pos.PosService.OrderDetail;
import com.terp.erp.commerce.pos.PosService.OrderRow;
import com.terp.erp.commerce.pos.PosService.PreviewDto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "POS")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pos")
@SecurityRequirement(name = "bearerAuth")
public class PosController {
    private static final String COMPANY = "Super admin only: the company to act on";
    private static final String CART_ERRORS = "Discount above subtotal + taxes (POS_005), inactive warehouse "
            + "(POS_006), super admin without company (POS_002), validation (COMMON_001)";
    private static final String REMOTE_404 = "Warehouse (INV_007), item (INV_009), customer (ACC_ codes) or bank "
            + "account (ACC_006) not in your company";
    private static final String PERIOD = "Default the last 30 days; at most 366 days (POS_007)";

    private final PosService service;
    private final PosReportService reports;
    private final PosDashboardService dashboard;

    @Operation(summary = "Cart totals for the checkout screen",
            description = "Prices and taxes from the items; same item twice = one line. Nothing is written.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = CART_ERRORS),
            @ApiResponse(responseCode = "404", description = REMOTE_404)})
    @PreAuthorize("@PermissionChecker.has('POS_SELL')")
    @PostMapping("/orders/preview")
    public ApiMessageDto<PreviewDto> preview(@Valid @RequestBody CartForm form) {
        return ApiMessageDto.ok(service.preview(form));
    }

    @Operation(summary = "Pay: create the POS order", description = "Stock leaves the warehouse (never below zero) "
            + "and the total is recorded as income in the bank account; the order gets its POS and receipt numbers.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "No bank account (POS_003), inactive bank account "
            + "(POS_004), not enough stock (INV_017), " + CART_ERRORS),
            @ApiResponse(responseCode = "404", description = REMOTE_404),
            @ApiResponse(responseCode = "503", description = "Inventory (SAL_015) or accounting (SAL_016) unavailable")})
    @PreAuthorize("@PermissionChecker.has('POS_SELL')")
    @PostMapping("/orders")
    public ApiMessageDto<OrderDetail> checkout(@Valid @RequestBody CartForm form) {
        return ApiMessageDto.ok(service.checkout(form));
    }

    @Operation(summary = "POS orders", description = "Filters customer, warehouse, date range; keyword = POS number "
            + "or customer name.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (POS_002)"))
    @PreAuthorize("@PermissionChecker.has('POS_VIEW')")
    @GetMapping("/orders")
    public ApiMessageDto<ResponseListDto<OrderRow>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, customerId, warehouseId, from, to, keyword,
                pageable)));
    }

    @Operation(summary = "POS order with lines and taxes")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such order in your company (POS_001)"))
    @PreAuthorize("@PermissionChecker.has('POS_VIEW')")
    @GetMapping("/orders/{id}")
    public ApiMessageDto<OrderDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Receipt PDF", description = "Receipt (re)print. POS_VIEW prints any order of your company; "
            + "with only POS_SELL you print only the orders you created.")
    @ApiResponses({@ApiResponse(responseCode = "403", description = "Neither POS_VIEW nor POS_SELL (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such order in your company, or (POS_SELL only) "
                    + "an order another user created (POS_001)")})
    @PreAuthorize("@PermissionChecker.hasAny('POS_VIEW','POS_SELL')")
    @GetMapping("/orders/{id}/pdf")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        PosService.Receipt r = service.receipt(id);
        return pdf(r.pdf(), r.fileName());
    }

    @Operation(summary = "Barcode labels PDF", description = "Code 128 of each item's SKU, copies per item; at most "
            + PosService.MAX_LABELS + " labels in all.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Too many labels (POS_008), validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "Warehouse (INV_007) or item (INV_009) not in your company")})
    @PreAuthorize("@PermissionChecker.has('POS_SELL')")
    @PostMapping("/barcodes/pdf")
    public ResponseEntity<byte[]> barcodes(@Valid @RequestBody BarcodeForm form) {
        return pdf(service.barcodesPdf(form), "barcodes.pdf");
    }

    @Operation(summary = "Sales report: daily, monthly, by warehouse", description = PERIOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad period (POS_007), no company (POS_002)"))
    @PreAuthorize("@PermissionChecker.has('POS_REPORT')")
    @GetMapping("/reports/sales")
    public ApiMessageDto<SalesReport> sales(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(reports.sales(companyId, from, to));
    }

    @Operation(summary = "Product report: top 10 by revenue, quantity split, table", description = PERIOD
            + ". Revenue = line amount + tax, before the order discount.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad period (POS_007), no company (POS_002)"))
    @PreAuthorize("@PermissionChecker.has('POS_REPORT')")
    @GetMapping("/reports/products")
    public ApiMessageDto<ProductReport> products(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(reports.products(companyId, from, to));
    }

    @Operation(summary = "Customer report: top 10 by spend, average order value, last order", description = PERIOD
            + ". Walk-in sales are one row with customerId null.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad period (POS_007), no company (POS_002)"))
    @PreAuthorize("@PermissionChecker.has('POS_REPORT')")
    @GetMapping("/reports/customers")
    public ApiMessageDto<CustomerReport> customers(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(reports.customers(companyId, from, to));
    }

    @Operation(summary = "POS dashboard: today, year totals, monthly orders, hour x weekday heat map, top products",
            description = "todayOrders / todayRevenue = orders dated today in tz; totals and monthly over the year "
                    + "(default this year); heatmap = 168 cells (dayOfWeek 1 = Monday .. 7, hour 0-23) from the "
                    + "order's creation time in tz; topProducts = top 10 by revenue of the year. tz = IANA zone, "
                    + "default Asia/Ho_Chi_Minh.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "No company (POS_002), unknown tz (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('POS_REPORT')")
    @GetMapping("/reports/dashboard")
    public ApiMessageDto<PosDashboardService.PosDashboard> dashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer year,
            @Parameter(description = "IANA time zone, default Asia/Ho_Chi_Minh")
            @RequestParam(required = false) String tz) {
        return ApiMessageDto.ok(dashboard.dashboard(companyId, year, tz));
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}
