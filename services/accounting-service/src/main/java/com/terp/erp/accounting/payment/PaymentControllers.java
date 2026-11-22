package com.terp.erp.accounting.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.accounting.payment.PaymentService.OpenItems;
import com.terp.erp.accounting.payment.PaymentService.PaymentDto;
import com.terp.erp.accounting.payment.PaymentService.PaymentForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.time.LocalDate;

/** Customer payments and vendor payments: same endpoints, the class decides the kind (ERP manual 12.4). */
final class PaymentControllers {
    static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    static final String NOT_FOUND = "No such payment in your company (ACC_050)";
    static final String CREATE_ERRORS = "Invoice of another party (ACC_052), not open (ACC_053), over its balance "
            + "(ACC_054), listed twice (ACC_055), note not usable (ACC_056) or over its balance (ACC_057), notes over "
            + "the allocations (ACC_058), inactive bank account (ACC_008), super admin without companyId (ACC_001), "
            + "validation (COMMON_001); commerce codes (SAL_/PUR_) relayed";
    static final String CREATE_NOT_FOUND = "Party (ACC_002 / ACC_004), bank account (ACC_006), note (ACC_061) not in "
            + "your company; invoice of another company (SAL_001 / PUR_001 relayed)";

    private PaymentControllers() {
    }

    @SecurityRequirement(name = "bearerAuth")
    public abstract static class Base {
        private final PaymentService service;
        private final EPaymentKind kind;

        Base(PaymentService service, EPaymentKind kind) {
            this.service = service;
            this.kind = kind;
        }

        @Operation(summary = "Payments", description = "partyId = customer (customer payments) or vendor (vendor "
                + "payments); from/to on the payment date.")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
        @PreAuthorize("@PermissionChecker.has('PAYMENT_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<PaymentDto>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long partyId, @RequestParam(required = false) EPaymentStatus status,
                @RequestParam(required = false) Long bankAccountId,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
                Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(kind, companyId, partyId, status, bankAccountId,
                    from, to, pageable)));
        }

        @Operation(summary = "Open items of a party for the payment form", description = "The party's POSTED and "
                + "PARTIAL invoices (from commerce-service; the caller needs SALES_VIEW / PURCHASE_VIEW there) and "
                + "its approved notes with a balance left.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"),
                @ApiResponse(responseCode = "404", description = "Party not in your company (ACC_002 / ACC_004)"),
                @ApiResponse(responseCode = "503", description = "Commerce unavailable (ACC_060)")})
        @PreAuthorize("@PermissionChecker.has('PAYMENT_MANAGE')")
        @GetMapping("/open-items")
        public ApiMessageDto<OpenItems> openItems(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam Long partyId) {
            return ApiMessageDto.ok(service.openItems(kind, companyId, partyId));
        }

        @Operation(summary = "Payment with its allocations and notes")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('PAYMENT_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<PaymentDto> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(kind, id));
        }

        @Operation(summary = "Record a payment (PENDING)", description = "Number CP-/VP-YYYY-MM-NNN. amount (cash) = "
                + "allocations - notes applied. Invoices and notes are checked now and again when it clears.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = CREATE_ERRORS),
                @ApiResponse(responseCode = "404", description = CREATE_NOT_FOUND),
                @ApiResponse(responseCode = "503", description = "Commerce unavailable (ACC_060)")})
        @PreAuthorize("@PermissionChecker.has('PAYMENT_MANAGE')")
        @PostMapping
        public ApiMessageDto<PaymentDto> create(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @Valid @RequestBody PaymentForm form) {
            return ApiMessageDto.ok(service.create(kind, companyId, form));
        }

        @Operation(summary = "Delete a pending payment")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not pending (ACC_051)"),
                @ApiResponse(responseCode = "404", description = NOT_FOUND)})
        @PreAuthorize("@PermissionChecker.has('PAYMENT_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(kind, id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Mark as cleared: PENDING → CLEARED", description = "Pays each invoice in commerce-service "
                + "(the caller needs PAYMENT_CLEAR there too), lowers the notes' balances, records the bank "
                + "transaction (CREDIT for a customer, DEBIT for a vendor) and posts the journal entry (customer: Dr "
                + "bank / Cr receivable; vendor: Dr payable / Cr bank). A retry after a failure pays no invoice twice.")
        @ApiResponses({
                @ApiResponse(responseCode = "400", description = "Not pending (ACC_051), inactive bank account "
                        + "(ACC_008), note no longer usable (ACC_056 / ACC_057), invoice balance now too small "
                        + "(SAL_022 / PUR_015 relayed), journal codes of the ledger"),
                @ApiResponse(responseCode = "404", description = NOT_FOUND),
                @ApiResponse(responseCode = "503", description = "Commerce unavailable (ACC_060)")})
        @PreAuthorize("@PermissionChecker.has('PAYMENT_CLEAR')")
        @PostMapping("/{id}/clear")
        public ApiMessageDto<PaymentDto> clear(@PathVariable Long id) {
            return ApiMessageDto.ok(service.clear(kind, id));
        }

        @Operation(summary = "Cancel: PENDING → CANCELLED")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not pending (ACC_051)"),
                @ApiResponse(responseCode = "404", description = NOT_FOUND)})
        @PreAuthorize("@PermissionChecker.has('PAYMENT_CLEAR')")
        @PostMapping("/{id}/cancel")
        public ApiMessageDto<PaymentDto> cancel(@PathVariable Long id) {
            return ApiMessageDto.ok(service.cancel(kind, id));
        }
    }

    @Tag(name = "Customer payments")
    @RestController
    @RequestMapping("/api/v1/accounting/customer-payments")
    public static class CustomerPaymentController extends Base {
        public CustomerPaymentController(PaymentService service) {
            super(service, EPaymentKind.CUSTOMER);
        }
    }

    @Tag(name = "Vendor payments")
    @RestController
    @RequestMapping("/api/v1/accounting/vendor-payments")
    public static class VendorPaymentController extends Base {
        public VendorPaymentController(PaymentService service) {
            super(service, EPaymentKind.VENDOR);
        }
    }
}
