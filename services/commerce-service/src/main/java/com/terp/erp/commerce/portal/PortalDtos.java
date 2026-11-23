package com.terp.erp.commerce.portal;

import com.terp.erp.commerce.purchase.PurchaseInvoice;
import com.terp.erp.commerce.purchase.PurchaseReturn;
import com.terp.erp.commerce.sales.EDocumentKind;
import com.terp.erp.commerce.sales.EDocumentStatus;
import com.terp.erp.commerce.sales.SalesDocument;
import com.terp.erp.commerce.sales.SalesDocumentLine;
import com.terp.erp.commerce.salesreturn.SalesReturn;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a customer / vendor sees of a commerce document: amounts, dates, status and the priced lines. Never internal
 * notes, item / warehouse ids, unit cost, journal state, conversion links or who created it.
 */
public final class PortalDtos {
    private PortalDtos() {
    }

    public record DocLine(String itemName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                          BigDecimal discountPct, String taxName, BigDecimal taxRate, BigDecimal discount,
                          BigDecimal taxAmount, BigDecimal lineTotal) {
        static DocLine of(SalesDocumentLine l) {
            return new DocLine(l.getItemName(), l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(),
                    l.getTaxName(), l.getTaxRate(), l.getDiscount(), l.getTaxAmount(), l.getLineTotal());
        }

        static DocLine of(PurchaseInvoice.Line l) {
            return new DocLine(l.getItemName(), l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(),
                    l.getTaxName(), l.getTaxRate(), l.getDiscount(), l.getTaxAmount(), l.getLineTotal());
        }
    }

    /** Quotation, proposal or sales invoice of my customer. */
    public record SalesRow(String id, EDocumentKind kind, String number, LocalDate docDate,
                           @Schema(description = "Due date (expiry for a quotation or proposal)") LocalDate dueDate,
                           EDocumentStatus status, BigDecimal subtotal, BigDecimal discount, BigDecimal tax,
                           BigDecimal total,
                           @Schema(description = "Invoices: still to pay") BigDecimal balance,
                           @Schema(description = "Invoice posted or partly paid past its due date") boolean overdue,
                           @Schema(description = "Quotation or proposal still SENT past its due date") boolean expired,
                           int versionNo) {
        static SalesRow of(SalesDocument d, LocalDate today) {
            boolean late = d.getDueDate().isBefore(today);
            boolean invoice = d.getKind() == EDocumentKind.INVOICE;
            boolean overdue = invoice && late
                    && (d.getStatus() == EDocumentStatus.POSTED || d.getStatus() == EDocumentStatus.PARTIAL);
            boolean expired = !invoice && late && d.getStatus() == EDocumentStatus.SENT;
            return new SalesRow(d.getId().toString(), d.getKind(), d.getNumber(), d.getDocDate(), d.getDueDate(),
                    d.getStatus(), d.getSubtotal(), d.getDiscountTotal(), d.getTaxTotal(), d.getTotal(),
                    invoice ? d.getBalance() : null, overdue, expired, d.getVersionNo());
        }
    }

    public record SalesDetail(SalesRow document, String paymentTerms, List<DocLine> lines) {
        static SalesDetail of(SalesDocument d, LocalDate today) {
            return new SalesDetail(SalesRow.of(d, today), d.getPaymentTerms(),
                    d.getLines().stream().map(DocLine::of).toList());
        }
    }

    public record ReturnLine(String itemName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal discountPct, BigDecimal taxRate, BigDecimal discount, BigDecimal taxAmount,
                             BigDecimal lineTotal, String reason) {
        static ReturnLine of(SalesReturn.Line l) {
            return new ReturnLine(l.getItemName(), l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(),
                    l.getTaxRate(), l.getDiscount(), l.getTaxAmount(), l.getLineTotal(), l.getReason());
        }

        static ReturnLine of(PurchaseReturn.Line l) {
            return new ReturnLine(l.getItemName(), l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(),
                    l.getTaxRate(), l.getDiscount(), l.getTaxAmount(), l.getLineTotal(), l.getReason());
        }
    }

    /** Sales return of my customer or purchase return to my vendor; status APPROVED or COMPLETED. */
    public record ReturnRow(String id, String number,
                            @Schema(description = "The invoice returned against (visible in the same portal)")
                            String invoiceId,
                            LocalDate returnDate, String status, BigDecimal subtotal, BigDecimal discount,
                            BigDecimal tax, BigDecimal total) {
        static ReturnRow of(SalesReturn r) {
            return new ReturnRow(r.getId().toString(), r.getNumber(), r.getInvoiceId().toString(), r.getReturnDate(),
                    r.getStatus().name(), r.getSubtotal(), r.getDiscountTotal(), r.getTaxTotal(), r.getTotal());
        }

        static ReturnRow of(PurchaseReturn r) {
            return new ReturnRow(r.getId().toString(), r.getNumber(), r.getInvoiceId().toString(), r.getReturnDate(),
                    r.getStatus().name(), r.getSubtotal(), r.getDiscountTotal(), r.getTaxTotal(), r.getTotal());
        }
    }

    public record ReturnDetail(ReturnRow ret, String reason, List<ReturnLine> lines) {
        static ReturnDetail of(SalesReturn r) {
            return new ReturnDetail(ReturnRow.of(r), r.getReason(), r.getLines().stream().map(ReturnLine::of).toList());
        }

        static ReturnDetail of(PurchaseReturn r) {
            return new ReturnDetail(ReturnRow.of(r), r.getReason(), r.getLines().stream().map(ReturnLine::of).toList());
        }
    }

    /** Purchase invoice billed by my vendor; status POSTED, PARTIAL or PAID. */
    public record PurchaseRow(String id, String number, LocalDate invoiceDate, LocalDate dueDate,
                              PurchaseInvoice.EStatus status, BigDecimal subtotal, BigDecimal discount,
                              BigDecimal tax, BigDecimal total,
                              @Schema(description = "Still to be paid to the vendor") BigDecimal balance,
                              @Schema(description = "Posted or partly paid past its due date") boolean overdue) {
        static PurchaseRow of(PurchaseInvoice d, LocalDate today) {
            boolean overdue = d.getDueDate().isBefore(today) && (d.getStatus() == PurchaseInvoice.EStatus.POSTED
                    || d.getStatus() == PurchaseInvoice.EStatus.PARTIAL);
            return new PurchaseRow(d.getId().toString(), d.getNumber(), d.getInvoiceDate(), d.getDueDate(),
                    d.getStatus(), d.getSubtotal(), d.getDiscountTotal(), d.getTaxTotal(), d.getTotal(),
                    d.getBalance(), overdue);
        }
    }

    public record PurchaseDetail(PurchaseRow invoice, String paymentTerms, List<DocLine> lines) {
        static PurchaseDetail of(PurchaseInvoice d, LocalDate today) {
            return new PurchaseDetail(PurchaseRow.of(d, today), d.getPaymentTerms(),
                    d.getLines().stream().map(DocLine::of).toList());
        }
    }
}
