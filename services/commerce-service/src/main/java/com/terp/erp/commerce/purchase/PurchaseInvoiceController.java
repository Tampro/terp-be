package com.terp.erp.commerce.purchase;

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
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.commerce.purchase.PurchaseInvoiceService.InvoiceDetail;
import com.terp.erp.commerce.purchase.PurchaseInvoiceService.InvoiceForm;
import com.terp.erp.commerce.purchase.PurchaseInvoiceService.InvoiceRow;

import java.time.LocalDate;
import java.util.Map;

@Tag(name = "Purchase — invoices")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/purchase/invoices")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseInvoiceController {
    private static final String COMPANY = "Super admin only: the company to act on (required for a super admin)";
    private static final String WRITE_ERRORS = "Not a draft (PUR_002), due before invoice date (PUR_004), validation "
            + "(COMMON_001); vendor or item of another service (ACC_ / INV_ codes relayed)";
    private final PurchaseInvoiceService service;

    @Operation(summary = "Purchase invoices", description = "Number PI-YYYY-MM-NNN. overdue = posted or partly paid "
            + "past the due date.")
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<InvoiceRow>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long vendorId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) PurchaseInvoice.EStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Matches the number or the vendor") @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean overdue,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, vendorId, warehouseId, from, to, keyword,
                status, overdue, pageable)));
    }

    @Operation(summary = "Unified calendar: purchase invoices falling due in a period",
            description = "Invoices with dueDate in [from, to] (at most 62 days), by due date; every status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or longer than 62 days "
            + "(PUR_013), super admin without companyId (PUR_003)"))
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping("/calendar")
    public ApiMessageDto<java.util.List<PurchaseInvoiceService.CalendarEntry>> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.calendar(companyId, from, to));
    }

    @Operation(summary = "Count per status", description = "Same filters as the list, without status.")
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<Map<PurchaseInvoice.EStatus, Long>> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long vendorId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, vendorId, warehouseId, from, to, keyword));
    }

    @Operation(summary = "Purchase invoice with its lines")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such invoice in your company (PUR_001)"))
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<InvoiceDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a purchase invoice (DRAFT)", description = "Unit price defaults to the item's "
            + "purchase price.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = WRITE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('PURCHASE_MANAGE')")
    @PostMapping
    public ApiMessageDto<InvoiceDetail> create(@Valid @RequestBody InvoiceForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a draft", description = "Full replacement; the number stays.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
            @ApiResponse(responseCode = "404", description = "PUR_001")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<InvoiceDetail> update(@PathVariable Long id, @Valid @RequestBody InvoiceForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a draft")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (PUR_002)"),
            @ApiResponse(responseCode = "404", description = "PUR_001")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Post: DRAFT → POSTED", description = "Stock lines go into the receiving warehouse.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (PUR_002), no warehouse for stock "
            + "items (PUR_005), stock errors (INV_ codes relayed)"),
            @ApiResponse(responseCode = "404", description = "PUR_001"),
            @ApiResponse(responseCode = "503", description = "Inventory unavailable (SAL_015)")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_POST')")
    @PostMapping("/{id}/post")
    public ApiMessageDto<InvoiceDetail> post(@PathVariable Long id) {
        return ApiMessageDto.ok(service.post(id));
    }
}
