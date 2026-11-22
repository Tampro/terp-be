package com.terp.erp.commerce.sales;

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
import com.terp.erp.commerce.sales.SalesDtos.DocumentDetail;
import com.terp.erp.commerce.sales.SalesDtos.DocumentForm;
import com.terp.erp.commerce.sales.SalesDtos.DocumentRow;

import java.time.LocalDate;
import java.util.Map;

/** The three document controllers: same shape, own path, own permissions. */
final class SalesControllers {
    static final String COMPANY = "Super admin only: the company to act on (required for a super admin)";
    static final String WRITE_ERRORS = "Not a draft (SAL_002), due before date (SAL_007), item type not allowed "
            + "(SAL_008), service-wise rules (SAL_019), validation (COMMON_001); customer or item of another service "
            + "(ACC_ / INV_ codes relayed)";

    private SalesControllers() {
    }

    @Tag(name = "Sales — quotations")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/sales/quotations")
    @SecurityRequirement(name = "bearerAuth")
    public static class QuotationController {
        private static final EDocumentKind KIND = EDocumentKind.QUOTATION;
        private final SalesDocumentService service;

        @Operation(summary = "Quotations", description = "Number QT-YYYY-MM-NNN. expired = still SENT past its due date.")
        @PreAuthorize("@PermissionChecker.has('QUOTATION_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<DocumentRow>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long customerId, @RequestParam(required = false) EDocumentStatus status,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @Parameter(description = "Matches the number or the customer") @RequestParam(required = false) String keyword,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(KIND, companyId, customerId, null, from, to,
                    keyword, status, false, pageable)));
        }

        @Operation(summary = "Quotation counts per status (tabs)")
        @PreAuthorize("@PermissionChecker.has('QUOTATION_VIEW')")
        @GetMapping("/status-counts")
        public ApiMessageDto<Map<EDocumentStatus, Long>> counts(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
            return ApiMessageDto.ok(service.statusCounts(KIND, companyId, null, null, null, null, null));
        }

        @Operation(summary = "Quotation with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "No such quotation in your company (SAL_001)"))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<DocumentDetail> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(KIND, id));
        }

        @Operation(summary = "Create a quotation (DRAFT)")
        @ApiResponses(@ApiResponse(responseCode = "400", description = WRITE_ERRORS))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PostMapping
        public ApiMessageDto<DocumentDetail> create(@Valid @RequestBody DocumentForm form) {
            return ApiMessageDto.ok(service.create(KIND, form));
        }

        @Operation(summary = "Edit a draft quotation", description = "Full replacement of the header and lines.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
                @ApiResponse(responseCode = "404", description = "SAL_001")})
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PutMapping("/{id}")
        public ApiMessageDto<DocumentDetail> update(@PathVariable Long id, @Valid @RequestBody DocumentForm form) {
            return ApiMessageDto.ok(service.update(KIND, id, form));
        }

        @Operation(summary = "Delete a draft quotation")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (SAL_002)"),
                @ApiResponse(responseCode = "404", description = "SAL_001")})
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(KIND, id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Send: DRAFT → SENT")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a draft (SAL_002)"))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PostMapping("/{id}/send")
        public ApiMessageDto<DocumentDetail> send(@PathVariable Long id) {
            return ApiMessageDto.ok(service.send(KIND, id));
        }

        @Operation(summary = "Accept: SENT → ACCEPTED")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PostMapping("/{id}/accept")
        public ApiMessageDto<DocumentDetail> accept(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(KIND, id, true));
        }

        @Operation(summary = "Reject: SENT → REJECTED")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PostMapping("/{id}/reject")
        public ApiMessageDto<DocumentDetail> reject(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(KIND, id, false));
        }

        @Operation(summary = "Duplicate into a new draft", description = "Same customer and lines, dated today, new number.")
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PostMapping("/{id}/duplicate")
        public ApiMessageDto<DocumentDetail> duplicate(@PathVariable Long id) {
            return ApiMessageDto.ok(service.duplicate(KIND, id));
        }

        @Operation(summary = "New version of a sent, accepted or rejected quotation (new draft)")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Still a draft (SAL_017)"))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE')")
        @PostMapping("/{id}/versions")
        public ApiMessageDto<DocumentDetail> version(@PathVariable Long id) {
            return ApiMessageDto.ok(service.newVersion(id));
        }

        @Operation(summary = "Convert an accepted quotation to a draft invoice (once)")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not accepted (SAL_004), already converted (SAL_005)"))
        @PreAuthorize("@PermissionChecker.has('QUOTATION_MANAGE') and @PermissionChecker.has('SALES_MANAGE')")
        @PostMapping("/{id}/convert")
        public ApiMessageDto<DocumentDetail> convert(@PathVariable Long id) {
            return ApiMessageDto.ok(service.convert(KIND, id));
        }
    }

    @Tag(name = "Sales — proposals")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/sales/proposals")
    @SecurityRequirement(name = "bearerAuth")
    public static class ProposalController {
        private static final EDocumentKind KIND = EDocumentKind.PROPOSAL;
        private final SalesDocumentService service;

        @Operation(summary = "Proposals", description = "Number SP-YYYY-MM-NNN. expired = still SENT past its due date.")
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<DocumentRow>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long customerId, @RequestParam(required = false) EDocumentStatus status,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @Parameter(description = "Matches the number or the customer") @RequestParam(required = false) String keyword,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(KIND, companyId, customerId, null, from, to,
                    keyword, status, false, pageable)));
        }

        @Operation(summary = "Proposal counts per status (tabs)")
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping("/status-counts")
        public ApiMessageDto<Map<EDocumentStatus, Long>> counts(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
            return ApiMessageDto.ok(service.statusCounts(KIND, companyId, null, null, null, null, null));
        }

        @Operation(summary = "Proposal with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "No such proposal in your company (SAL_001)"))
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<DocumentDetail> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(KIND, id));
        }

        @Operation(summary = "Create a proposal (DRAFT)")
        @ApiResponses(@ApiResponse(responseCode = "400", description = WRITE_ERRORS))
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PostMapping
        public ApiMessageDto<DocumentDetail> create(@Valid @RequestBody DocumentForm form) {
            return ApiMessageDto.ok(service.create(KIND, form));
        }

        @Operation(summary = "Edit a draft proposal", description = "Full replacement of the header and lines.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
                @ApiResponse(responseCode = "404", description = "SAL_001")})
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PutMapping("/{id}")
        public ApiMessageDto<DocumentDetail> update(@PathVariable Long id, @Valid @RequestBody DocumentForm form) {
            return ApiMessageDto.ok(service.update(KIND, id, form));
        }

        @Operation(summary = "Delete a draft proposal")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (SAL_002)"),
                @ApiResponse(responseCode = "404", description = "SAL_001")})
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(KIND, id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Send: DRAFT → SENT")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a draft (SAL_002)"))
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PostMapping("/{id}/send")
        public ApiMessageDto<DocumentDetail> send(@PathVariable Long id) {
            return ApiMessageDto.ok(service.send(KIND, id));
        }

        @Operation(summary = "Accept: SENT → ACCEPTED")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"))
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PostMapping("/{id}/accept")
        public ApiMessageDto<DocumentDetail> accept(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(KIND, id, true));
        }

        @Operation(summary = "Reject: SENT → REJECTED")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"))
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PostMapping("/{id}/reject")
        public ApiMessageDto<DocumentDetail> reject(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(KIND, id, false));
        }

        @Operation(summary = "Convert an accepted proposal to a draft invoice (once; the proposal keeps its status)")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Not accepted (SAL_004), already converted (SAL_005)"))
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PostMapping("/{id}/convert")
        public ApiMessageDto<DocumentDetail> convert(@PathVariable Long id) {
            return ApiMessageDto.ok(service.convert(KIND, id));
        }
    }

    @Tag(name = "Sales — invoices")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/sales/invoices")
    @SecurityRequirement(name = "bearerAuth")
    public static class InvoiceController {
        private static final EDocumentKind KIND = EDocumentKind.INVOICE;
        private final SalesDocumentService service;

        @Operation(summary = "Sales invoices", description = "Number SI-YYYY-MM-NNN. overdue = posted or partly paid "
                + "past its due date; overdue=true lists only those.")
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<DocumentRow>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long customerId, @RequestParam(required = false) Long warehouseId,
                @RequestParam(required = false) EDocumentStatus status,
                @RequestParam(defaultValue = "false") boolean overdue,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @Parameter(description = "Matches the number or the customer") @RequestParam(required = false) String keyword,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(KIND, companyId, customerId, warehouseId, from, to,
                    keyword, status, overdue, pageable)));
        }

        @Operation(summary = "Unified calendar: invoices falling due in a period",
                description = "Invoices with dueDate in [from, to] (at most 62 days), by due date; every status.")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or longer than 62 days "
                + "(SAL_020), super admin without companyId (SAL_006)"))
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping("/calendar")
        public ApiMessageDto<java.util.List<SalesDocumentService.CalendarEntry>> calendar(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
            return ApiMessageDto.ok(service.calendar(KIND, companyId, from, to));
        }

        @Operation(summary = "Invoice counts per status (tabs)")
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping("/status-counts")
        public ApiMessageDto<Map<EDocumentStatus, Long>> counts(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
            return ApiMessageDto.ok(service.statusCounts(KIND, companyId, null, null, null, null, null));
        }

        @Operation(summary = "Invoice with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = "No such invoice in your company (SAL_001)"))
        @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<DocumentDetail> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(KIND, id));
        }

        @Operation(summary = "Create an invoice (DRAFT)", description = "invoiceType PRODUCT (default; warehouse needed "
                + "to post) or SERVICE (service items, quantity 1, no warehouse).")
        @ApiResponses(@ApiResponse(responseCode = "400", description = WRITE_ERRORS))
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PostMapping
        public ApiMessageDto<DocumentDetail> create(@Valid @RequestBody DocumentForm form) {
            return ApiMessageDto.ok(service.create(KIND, form));
        }

        @Operation(summary = "Edit a draft invoice", description = "Full replacement of the header and lines.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
                @ApiResponse(responseCode = "404", description = "SAL_001")})
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @PutMapping("/{id}")
        public ApiMessageDto<DocumentDetail> update(@PathVariable Long id, @Valid @RequestBody DocumentForm form) {
            return ApiMessageDto.ok(service.update(KIND, id, form));
        }

        @Operation(summary = "Delete a draft invoice")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (SAL_002)"),
                @ApiResponse(responseCode = "404", description = "SAL_001")})
        @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(KIND, id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Post: DRAFT → POSTED", description = "A product-wise invoice takes its stocked lines out "
                + "of its warehouse (all or nothing). The invoice can no longer be edited or deleted.")
        @ApiResponses({
                @ApiResponse(responseCode = "400", description = "Not a draft (SAL_002), no warehouse (SAL_009), "
                        + "not enough stock (INV_ code relayed)"),
                @ApiResponse(responseCode = "503", description = "Inventory unavailable (SAL_015)")})
        @PreAuthorize("@PermissionChecker.has('SALES_POST')")
        @PostMapping("/{id}/post")
        public ApiMessageDto<DocumentDetail> post(@PathVariable Long id) {
            return ApiMessageDto.ok(service.post(id));
        }
    }
}
