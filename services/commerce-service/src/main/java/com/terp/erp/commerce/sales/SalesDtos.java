package com.terp.erp.commerce.sales;

import io.swagger.v3.oas.annotations.media.Schema;
import com.terp.erp.commerce.common.EJournalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SalesDtos {
    private SalesDtos() {
    }

    public record LineForm(
            @Schema(description = "Item id of inventory-service", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long itemId,
            @Schema(description = "Quantity; omit = 1. A service-wise invoice takes 1 only.", example = "2")
            @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Schema(description = "Unit price; omit = the item's sale price", example = "150000")
            @DecimalMin("0") BigDecimal unitPrice,
            @Schema(description = "Discount in percent", example = "10")
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountPct) {
    }

    /** Create and full-replacement edit of a draft. */
    public record DocumentForm(
            @Schema(description = "Super admin only: the company to act on", example = "10") Long companyId,
            @Schema(description = "Customer id of accounting-service", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long customerId,
            @Schema(description = "Warehouse id of inventory-service; required to post a product-wise invoice", example = "2")
            Long warehouseId,
            @Schema(description = "Invoices only: PRODUCT (default) or SERVICE", example = "PRODUCT")
            EInvoiceType invoiceType,
            @Schema(description = "Document date", example = "2026-11-21", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate docDate,
            @Schema(description = "Due date (expiry for a quotation or proposal)", example = "2026-12-21",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate dueDate,
            @Schema(description = "Payment terms", example = "Net 30") @Size(max = 100) String paymentTerms,
            @Schema(description = "Notes", example = "Delivery to the main office") @Size(max = 5000) String notes,
            @Schema(description = "Lines, 1 to 200", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty @Size(max = 200) List<@Valid @NotNull LineForm> lines,
            @Schema(description = "Invoices only: add the due date to the company's Google Calendar when posted "
                    + "(the Google Calendar integration must be on); omitted = false", example = "false")
            Boolean syncToGoogleCalendar) {
        public DocumentForm(Long companyId, Long customerId, Long warehouseId, EInvoiceType invoiceType,
                            LocalDate docDate, LocalDate dueDate, String paymentTerms, String notes,
                            List<LineForm> lines) {
            this(companyId, customerId, warehouseId, invoiceType, docDate, dueDate, paymentTerms, notes, lines, null);
        }
    }

    public record LineDto(String id, String itemId, String itemType, String itemName, String sku, BigDecimal quantity,
                          BigDecimal unitPrice, BigDecimal discountPct, String taxName, BigDecimal taxRate,
                          BigDecimal discount, BigDecimal taxAmount, BigDecimal lineTotal) {
        static LineDto of(SalesDocumentLine l) {
            return new LineDto(String.valueOf(l.getId()), l.getItemId().toString(), l.getItemType(), l.getItemName(),
                    l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(), l.getTaxName(), l.getTaxRate(),
                    l.getDiscount(), l.getTaxAmount(), l.getLineTotal());
        }
    }

    public record DocumentRow(String id, EDocumentKind kind, String number, EInvoiceType invoiceType,
                              String customerId, String customerName, String warehouseId, LocalDate docDate,
                              LocalDate dueDate, BigDecimal subtotal, BigDecimal discount, BigDecimal tax,
                              BigDecimal total, BigDecimal balance, EDocumentStatus status,
                              @Schema(description = "Invoice posted or partly paid past its due date") boolean overdue,
                              @Schema(description = "Quotation or proposal still SENT past its due date") boolean expired,
                              int versionNo, String versionOfId, String convertedInvoiceId, String convertedFromId,
                              @Schema(description = "Invoices: accounting journal, written asynchronously after "
                                      + "posting - PENDING, POSTED or FAILED; null = draft / nothing to journal")
                              EJournalStatus journalStatus,
                              @Schema(description = "Accounting error code when journalStatus = FAILED",
                                      example = "ACC_070") String journalErrorCode,
                              Instant createdDate, Instant updatedDate) {
        static DocumentRow of(SalesDocument d, LocalDate today) {
            boolean late = d.getDueDate().isBefore(today);
            boolean overdue = d.getKind() == EDocumentKind.INVOICE && late
                    && (d.getStatus() == EDocumentStatus.POSTED || d.getStatus() == EDocumentStatus.PARTIAL);
            boolean expired = d.getKind() != EDocumentKind.INVOICE && late && d.getStatus() == EDocumentStatus.SENT;
            return new DocumentRow(d.getId().toString(), d.getKind(), d.getNumber(), d.getInvoiceType(),
                    d.getCustomerId().toString(), d.getCustomerName(), str(d.getWarehouseId()), d.getDocDate(),
                    d.getDueDate(), d.getSubtotal(), d.getDiscountTotal(), d.getTaxTotal(), d.getTotal(),
                    d.getBalance(), d.getStatus(), overdue, expired, d.getVersionNo(), str(d.getVersionOfId()),
                    str(d.getConvertedInvoiceId()), str(d.getConvertedFromId()), d.getJournalStatus(),
                    d.getJournalErrorCode(), d.getCreatedDate(),
                    d.getUpdatedDate());
        }
    }

    public record DocumentDetail(DocumentRow document, String paymentTerms, String notes, List<LineDto> lines,
                                 @Schema(description = "Invoices: the due date goes to Google Calendar when posted")
                                 boolean syncToGoogleCalendar) {
        static DocumentDetail of(SalesDocument d, LocalDate today) {
            return new DocumentDetail(DocumentRow.of(d, today), d.getPaymentTerms(), d.getNotes(),
                    d.getLines().stream().map(LineDto::of).toList(), d.isSyncToGoogleCalendar());
        }
    }

    static String str(Long v) {
        return v == null ? null : v.toString();
    }
}
