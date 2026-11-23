package com.terp.erp.commerce.portal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.commerce.portal.PortalDtos.PurchaseDetail;
import com.terp.erp.commerce.portal.PortalDtos.PurchaseRow;
import com.terp.erp.commerce.portal.PortalDtos.ReturnDetail;
import com.terp.erp.commerce.portal.PortalDtos.ReturnRow;
import com.terp.erp.commerce.portal.PortalDtos.SalesDetail;
import com.terp.erp.commerce.portal.PortalDtos.SalesRow;
import com.terp.erp.commerce.sales.EDocumentKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Client / vendor portal over commerce documents: only documents of the customer / vendor that accounting links to
 * the caller (no party id in the request), never drafts.
 */
public final class PortalControllers {
    static final String CLIENT_NOT_LINKED = "Account not linked to any customer / vendor (ACC_098, relayed from "
            + "accounting) or linked only to a vendor (SAL_026)";
    static final String VENDOR_NOT_LINKED = "Account not linked to any customer / vendor (ACC_098, relayed from "
            + "accounting) or linked only to a customer (PUR_018)";
    static final String DOC_404 = CLIENT_NOT_LINKED + "; no such non-draft document of my customer (SAL_001)";
    static final String RETURN_404 = CLIENT_NOT_LINKED + "; no such non-draft return of my customer (SAL_012)";
    static final String UNAVAILABLE = "Accounting unavailable (SAL_016)";

    private PortalControllers() {
    }

    static ResponseEntity<byte[]> pdf(PortalService.Pdf file) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @Tag(name = "Client portal — sales documents")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/portal/client")
    @SecurityRequirement(name = "bearerAuth")
    public static class ClientPortalController {
        private final PortalService service;

        @Operation(summary = "My quotations", description = "Quotations sent to my customer (no drafts).")
        @ApiResponses({@ApiResponse(responseCode = "404", description = CLIENT_NOT_LINKED),
                @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/quotations")
        public ApiMessageDto<ResponseListDto<SalesRow>> quotations(
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.clientDocuments(EDocumentKind.QUOTATION, pageable)));
        }

        @Operation(summary = "My quotation with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/quotations/{id}")
        public ApiMessageDto<SalesDetail> quotation(@PathVariable Long id) {
            return ApiMessageDto.ok(service.clientDocument(EDocumentKind.QUOTATION, id));
        }

        @Operation(summary = "My quotation as PDF")
        @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/quotations/{id}/pdf")
        public ResponseEntity<byte[]> quotationPdf(@PathVariable Long id) {
            return pdf(service.clientDocumentPdf(EDocumentKind.QUOTATION, id));
        }

        @Operation(summary = "Accept a quotation: SENT → ACCEPTED", description = "Recorded as done by my account.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"),
                @ApiResponse(responseCode = "404", description = DOC_404)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @PostMapping("/quotations/{id}/accept")
        public ApiMessageDto<SalesDetail> acceptQuotation(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(EDocumentKind.QUOTATION, id, true));
        }

        @Operation(summary = "Reject a quotation: SENT → REJECTED", description = "Recorded as done by my account.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"),
                @ApiResponse(responseCode = "404", description = DOC_404)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @PostMapping("/quotations/{id}/reject")
        public ApiMessageDto<SalesDetail> rejectQuotation(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(EDocumentKind.QUOTATION, id, false));
        }

        @Operation(summary = "My proposals", description = "Proposals sent to my customer (no drafts).")
        @ApiResponses({@ApiResponse(responseCode = "404", description = CLIENT_NOT_LINKED),
                @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/proposals")
        public ApiMessageDto<ResponseListDto<SalesRow>> proposals(
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.clientDocuments(EDocumentKind.PROPOSAL, pageable)));
        }

        @Operation(summary = "My proposal with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/proposals/{id}")
        public ApiMessageDto<SalesDetail> proposal(@PathVariable Long id) {
            return ApiMessageDto.ok(service.clientDocument(EDocumentKind.PROPOSAL, id));
        }

        @Operation(summary = "My proposal as PDF")
        @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/proposals/{id}/pdf")
        public ResponseEntity<byte[]> proposalPdf(@PathVariable Long id) {
            return pdf(service.clientDocumentPdf(EDocumentKind.PROPOSAL, id));
        }

        @Operation(summary = "Accept a proposal: SENT → ACCEPTED", description = "Recorded as done by my account.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"),
                @ApiResponse(responseCode = "404", description = DOC_404)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @PostMapping("/proposals/{id}/accept")
        public ApiMessageDto<SalesDetail> acceptProposal(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(EDocumentKind.PROPOSAL, id, true));
        }

        @Operation(summary = "Reject a proposal: SENT → REJECTED", description = "Recorded as done by my account.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not sent (SAL_003)"),
                @ApiResponse(responseCode = "404", description = DOC_404)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @PostMapping("/proposals/{id}/reject")
        public ApiMessageDto<SalesDetail> rejectProposal(@PathVariable Long id) {
            return ApiMessageDto.ok(service.decide(EDocumentKind.PROPOSAL, id, false));
        }

        @Operation(summary = "My invoices", description = "Posted, partly paid or paid sales invoices of my customer.")
        @ApiResponses({@ApiResponse(responseCode = "404", description = CLIENT_NOT_LINKED),
                @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/invoices")
        public ApiMessageDto<ResponseListDto<SalesRow>> invoices(
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.clientDocuments(EDocumentKind.INVOICE, pageable)));
        }

        @Operation(summary = "My invoice with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/invoices/{id}")
        public ApiMessageDto<SalesDetail> invoice(@PathVariable Long id) {
            return ApiMessageDto.ok(service.clientDocument(EDocumentKind.INVOICE, id));
        }

        @Operation(summary = "My invoice as PDF")
        @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/invoices/{id}/pdf")
        public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
            return pdf(service.clientDocumentPdf(EDocumentKind.INVOICE, id));
        }

        @Operation(summary = "My returns", description = "Approved or completed sales returns of my customer.")
        @ApiResponses({@ApiResponse(responseCode = "404", description = CLIENT_NOT_LINKED),
                @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/returns")
        public ApiMessageDto<ResponseListDto<ReturnRow>> returns(
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.clientReturns(pageable)));
        }

        @Operation(summary = "My return with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = RETURN_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/returns/{id}")
        public ApiMessageDto<ReturnDetail> salesReturn(@PathVariable Long id) {
            return ApiMessageDto.ok(service.clientReturn(id));
        }

        @Operation(summary = "My return as PDF")
        @ApiResponses(@ApiResponse(responseCode = "404", description = RETURN_404))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/returns/{id}/pdf")
        public ResponseEntity<byte[]> salesReturnPdf(@PathVariable Long id) {
            return pdf(service.clientReturnPdf(id));
        }
    }

    @Tag(name = "Vendor portal — purchase documents")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/portal/vendor")
    @SecurityRequirement(name = "bearerAuth")
    public static class VendorPortalController {
        private final PortalService service;

        @Operation(summary = "My purchase invoices", description = "Posted, partly paid or paid purchase invoices "
                + "billed by my vendor (no drafts). No PDF: purchase documents have none.")
        @ApiResponses({@ApiResponse(responseCode = "404", description = VENDOR_NOT_LINKED),
                @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
        @GetMapping("/purchase-invoices")
        public ApiMessageDto<ResponseListDto<PurchaseRow>> invoices(
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.vendorInvoices(pageable)));
        }

        @Operation(summary = "My purchase invoice with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = VENDOR_NOT_LINKED
                + "; no such non-draft purchase invoice of my vendor (PUR_001)"))
        @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
        @GetMapping("/purchase-invoices/{id}")
        public ApiMessageDto<PurchaseDetail> invoice(@PathVariable Long id) {
            return ApiMessageDto.ok(service.vendorInvoice(id));
        }

        @Operation(summary = "My purchase returns", description = "Approved or completed returns to my vendor.")
        @ApiResponses({@ApiResponse(responseCode = "404", description = VENDOR_NOT_LINKED),
                @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
        @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
        @GetMapping("/purchase-returns")
        public ApiMessageDto<ResponseListDto<ReturnRow>> returns(
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.vendorReturns(pageable)));
        }

        @Operation(summary = "My purchase return with its lines")
        @ApiResponses(@ApiResponse(responseCode = "404", description = VENDOR_NOT_LINKED
                + "; no such non-draft purchase return to my vendor (PUR_008)"))
        @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
        @GetMapping("/purchase-returns/{id}")
        public ApiMessageDto<ReturnDetail> purchaseReturn(@PathVariable Long id) {
            return ApiMessageDto.ok(service.vendorReturn(id));
        }
    }
}
