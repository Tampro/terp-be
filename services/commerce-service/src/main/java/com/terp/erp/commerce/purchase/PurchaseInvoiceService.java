package com.terp.erp.commerce.purchase;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.EJournalStatus;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.Money;
import com.terp.erp.commerce.common.Ownership;
import com.terp.erp.commerce.common.Ownership.Access;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.common.Postings;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Purchase invoices (ERP manual 8.1): number PI-YYYY-MM-NNN; only a DRAFT is edited or deleted. Posting puts the
 * stocked lines into the receiving warehouse (inventory-service, all-or-nothing, idempotent on the invoice id).
 * Journal entries and vendor payments come with accounting.
 */
@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {
    static final String STOCK_SOURCE = "PURCHASE_INVOICE";
    static final String PREFIX = "PI";
    static final String EVENT_POSTED = "purchase.invoice.posted";

    private final PurchaseInvoiceRepository invoices;
    private final Numbering numbering;
    private final InventoryClient inventory;
    private final AccountingClient accounting;
    private final Clock clock;
    private final OutboxPublisher outbox;

    public record LineForm(
            @Schema(description = "Item id of inventory-service", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long itemId,
            @Schema(description = "Quantity; omit = 1", example = "5")
            @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Schema(description = "Unit price; omit = the item's purchase price", example = "90000")
            @DecimalMin("0") BigDecimal unitPrice,
            @Schema(description = "Discount in percent", example = "0")
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountPct) {
    }

    /** Create and full-replacement edit of a draft. */
    public record InvoiceForm(
            @Schema(description = "Super admin only: the company to act on", example = "10") Long companyId,
            @Schema(description = "Vendor id of accounting-service", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long vendorId,
            @Schema(description = "Receiving warehouse of inventory-service; required to post stock items", example = "2")
            Long warehouseId,
            @Schema(description = "Invoice date", example = "2026-11-21", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate invoiceDate,
            @Schema(description = "Due date", example = "2026-12-21", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate dueDate,
            @Schema(description = "Payment terms", example = "Net 30") @Size(max = 100) String paymentTerms,
            @Schema(description = "Notes", example = "Delivered to the main warehouse") @Size(max = 5000) String notes,
            @Schema(description = "Lines, 1 to 200", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty @Size(max = 200) List<@Valid @NotNull LineForm> lines) {
    }

    public record LineDto(String id, String itemId, String itemType, String itemName, String sku, BigDecimal quantity,
                          BigDecimal unitPrice, BigDecimal discountPct, String taxName, BigDecimal taxRate,
                          BigDecimal discount, BigDecimal taxAmount, BigDecimal lineTotal) {
        static LineDto of(PurchaseInvoice.Line l) {
            return new LineDto(String.valueOf(l.getId()), l.getItemId().toString(), l.getItemType(), l.getItemName(),
                    l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(), l.getTaxName(), l.getTaxRate(),
                    l.getDiscount(), l.getTaxAmount(), l.getLineTotal());
        }
    }

    public record InvoiceRow(String id, String number, String vendorId, String vendorName, String warehouseId,
                             LocalDate invoiceDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal discount,
                             BigDecimal tax, BigDecimal total, BigDecimal balance, PurchaseInvoice.EStatus status,
                             @Schema(description = "Posted or partly paid past its due date") boolean overdue,
                             @Schema(description = "Accounting journal, written asynchronously after posting - "
                                     + "PENDING, POSTED or FAILED; null = draft / nothing to journal")
                             EJournalStatus journalStatus,
                             @Schema(description = "Accounting error code when journalStatus = FAILED",
                                     example = "ACC_070") String journalErrorCode,
                             Instant createdDate, Instant updatedDate) {
        static InvoiceRow of(PurchaseInvoice d, LocalDate today) {
            boolean overdue = d.getDueDate().isBefore(today) && (d.getStatus() == PurchaseInvoice.EStatus.POSTED
                    || d.getStatus() == PurchaseInvoice.EStatus.PARTIAL);
            return new InvoiceRow(d.getId().toString(), d.getNumber(), d.getVendorId().toString(), d.getVendorName(),
                    d.getWarehouseId() == null ? null : d.getWarehouseId().toString(), d.getInvoiceDate(),
                    d.getDueDate(), d.getSubtotal(), d.getDiscountTotal(), d.getTaxTotal(), d.getTotal(),
                    d.getBalance(), d.getStatus(), overdue, d.getJournalStatus(), d.getJournalErrorCode(),
                    d.getCreatedDate(), d.getUpdatedDate());
        }
    }

    /** One invoice on the unified calendar. */
    public record CalendarEntry(String id, String number, String vendorName, LocalDate invoiceDate, LocalDate dueDate,
                                PurchaseInvoice.EStatus status, BigDecimal total, BigDecimal balance) {
    }

    public record InvoiceDetail(InvoiceRow invoice, String paymentTerms, String notes, List<LineDto> lines) {
        static InvoiceDetail of(PurchaseInvoice d, LocalDate today) {
            return new InvoiceDetail(InvoiceRow.of(d, today), d.getPaymentTerms(), d.getNotes(),
                    d.getLines().stream().map(LineDto::of).toList());
        }
    }

    /** The unified calendar (report-service) shows a month with the weeks around it. */
    public static final int MAX_CALENDAR_DAYS = 62;
    static final String PURCHASE = "PURCHASE";

    /** TODO(assumption): a purchase invoice is placed on its due date (when it must be paid), drafts included. */
    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar(Long companyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(EPurchaseError.PUR_013, MAX_CALENDAR_DAYS);
        }
        Long company = TenantGuard.resolveCompany(companyId, EPurchaseError.PUR_003);
        return invoices.dueBetween(company, from, to, Ownership.owner(PURCHASE, Access.VIEW)).stream()
                .map(d -> new CalendarEntry(d.getId().toString(), d.getNumber(), d.getVendorName(),
                        d.getInvoiceDate(), d.getDueDate(), d.getStatus(), d.getTotal(), d.getBalance()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<InvoiceRow> search(Long companyId, Long vendorId, Long warehouseId, LocalDate from, LocalDate to,
                                   String keyword, PurchaseInvoice.EStatus status, boolean overdue,
                                   EJournalStatus journalStatus, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EPurchaseError.PUR_003);
        String owner = Ownership.owner(PURCHASE, Access.VIEW);
        LocalDate today = today();
        return invoices.search(company, vendorId, warehouseId, from, to, like(keyword), status, overdue,
                journalStatus, today, owner, pageable).map(d -> InvoiceRow.of(d, today));
    }

    @Transactional(readOnly = true)
    public Map<PurchaseInvoice.EStatus, Long> statusCounts(Long companyId, Long vendorId, Long warehouseId,
                                                           LocalDate from, LocalDate to, String keyword) {
        Long company = TenantGuard.resolveCompany(companyId, EPurchaseError.PUR_003);
        String owner = Ownership.owner(PURCHASE, Access.VIEW);
        Map<PurchaseInvoice.EStatus, Long> counts = new EnumMap<>(PurchaseInvoice.EStatus.class);
        for (PurchaseInvoice.EStatus s : PurchaseInvoice.EStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] r : invoices.countByStatus(company, vendorId, warehouseId, from, to, like(keyword), owner)) {
            counts.put((PurchaseInvoice.EStatus) r[0], ((Number) r[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public InvoiceDetail get(Long id) {
        return InvoiceDetail.of(require(id, Access.VIEW), today());
    }

    @Transactional
    public InvoiceDetail create(InvoiceForm f) {
        Long company = TenantGuard.resolveCompany(f.companyId(), EPurchaseError.PUR_003);
        PurchaseInvoice d = new PurchaseInvoice();
        d.setCompanyId(company);
        apply(d, f);
        d.setNumber(numbering.next(company, PREFIX, d.getInvoiceDate()));
        return InvoiceDetail.of(invoices.save(d), today());
    }

    /** Full replacement of a draft; the number never changes. */
    @Transactional
    public InvoiceDetail update(Long id, InvoiceForm f) {
        PurchaseInvoice d = requireDraft(id, Access.MANAGE);
        apply(d, f);
        return InvoiceDetail.of(d, today());
    }

    @Transactional
    public void delete(Long id) {
        requireDraft(id, Access.MANAGE).setDeleted(true);
    }

    /**
     * DRAFT → POSTED: stocked lines go into the receiving warehouse and the invoice journal (Dr 156 goods, Dr 6428
     * services, Dr 1331 VAT / Cr 331 total) is requested from accounting-service through the outbox, in the order of
     * {@link Postings}; a closed year (ACC_070) is relayed and the invoice stays a draft. The journal itself appears
     * asynchronously: journalStatus PENDING → POSTED | FAILED.
     */
    @Transactional
    public InvoiceDetail post(Long id) {
        PurchaseInvoice d = requireDraft(id, Access.POST);
        List<InventoryClient.MoveLine> moves = d.getLines().stream().filter(PurchaseInvoice.Line::stocked)
                .map(l -> new InventoryClient.MoveLine(l.getItemId(), d.getWarehouseId(), l.getQuantity())).toList();
        if (!moves.isEmpty() && d.getWarehouseId() == null) {
            throw ApiException.badRequest(EPurchaseError.PUR_005);
        }
        d.setJournalStatus(Postings.run(accounting, outbox, d.getCompanyId(), posting(d),
                () -> inventory.move(STOCK_SOURCE, d.getId(), moves)));
        d.setJournalErrorCode(null);
        d.setStatus(PurchaseInvoice.EStatus.POSTED);
        d.setPostedAt(Instant.now(clock));
        d.setBalance(d.getTotal().subtract(d.getPaidAmount()));
        // Notifications (webhook NEW_PURCHASE_INVOICE).
        outbox.publish(d.getCompanyId(), EVENT_POSTED, STOCK_SOURCE, d.getId(), Payload.of(
                "invoiceId", d.getId(), "number", d.getNumber(), "vendorId", d.getVendorId(),
                "vendorName", d.getVendorName(), "invoiceDate", d.getInvoiceDate(), "dueDate", d.getDueDate(),
                "total", d.getTotal()));
        return InvoiceDetail.of(d, today());
    }

    /**
     * Re-post of a journal accounting refused after posting (journalStatus FAILED): validated again synchronously,
     * then requested again; journalStatus → PENDING. Stock is not moved again.
     */
    @Transactional
    public InvoiceDetail retryJournal(Long id) {
        PurchaseInvoice d = require(id, Access.POST);
        if (d.getJournalStatus() != EJournalStatus.FAILED) {
            throw ApiException.badRequest(EPurchaseError.PUR_017);
        }
        Postings.retry(accounting, outbox, d.getCompanyId(), posting(d));
        d.setJournalStatus(EJournalStatus.PENDING);
        d.setJournalErrorCode(null);
        return InvoiceDetail.of(d, today());
    }

    static AccountingClient.Posting posting(PurchaseInvoice d) {
        BigDecimal goods = BigDecimal.ZERO, service = BigDecimal.ZERO;
        for (PurchaseInvoice.Line l : d.getLines()) {
            BigDecimal net = l.getLineTotal().subtract(l.getTaxAmount());
            if (l.stocked()) {
                goods = goods.add(net);
            } else {
                service = service.add(net);
            }
        }
        return new AccountingClient.Posting(STOCK_SOURCE, d.getId(), d.getInvoiceDate(), d.getNumber(),
                "Purchase invoice " + d.getNumber() + " - " + d.getVendorName(), goods, service, d.getTaxTotal(), null);
    }

    /**
     * For returns: a posted (or paid) invoice of the caller's company; a caller holding only *_OWN purchase codes
     * reaches only invoices they created (the Access.POST rule).
     */
    @Transactional(readOnly = true)
    public PurchaseInvoice requirePosted(Long id) {
        PurchaseInvoice d = require(id, Access.POST);
        if (d.getStatus() == PurchaseInvoice.EStatus.DRAFT) {
            throw ApiException.badRequest(EPurchaseError.PUR_006);
        }
        return d;
    }

    /**
     * For return create / edit: the posted invoice row locked until the transaction ends, so two returns of one
     * invoice are validated one after the other and cannot both take the last returnable quantity.
     */
    @Transactional
    public PurchaseInvoice lockPosted(Long id) {
        PurchaseInvoice d = invoices.lockById(id).orElseThrow(() -> ApiException.notFound(EPurchaseError.PUR_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), EPurchaseError.PUR_001);
        Ownership.check(Ownership.owner(PURCHASE, Access.POST), d.getCreatedBy(), EPurchaseError.PUR_001);
        if (d.getStatus() == PurchaseInvoice.EStatus.DRAFT) {
            throw ApiException.badRequest(EPurchaseError.PUR_006);
        }
        return d;
    }

    PurchaseInvoice require(Long id, Access access) {
        PurchaseInvoice d = invoices.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPurchaseError.PUR_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), EPurchaseError.PUR_001);
        Ownership.check(Ownership.owner(PURCHASE, access), d.getCreatedBy(), EPurchaseError.PUR_001);
        return d;
    }

    private PurchaseInvoice requireDraft(Long id, Access access) {
        PurchaseInvoice d = require(id, access);
        if (d.getStatus() != PurchaseInvoice.EStatus.DRAFT) {
            throw ApiException.badRequest(EPurchaseError.PUR_002);
        }
        return d;
    }

    /** Header and lines from the form; item data and the vendor come from their services with the caller's token. */
    private void apply(PurchaseInvoice d, InvoiceForm f) {
        if (f.dueDate().isBefore(f.invoiceDate())) {
            throw ApiException.badRequest(EPurchaseError.PUR_004);
        }
        AccountingClient.Vendor vendor = accounting.vendor(f.vendorId());
        d.setVendorId(f.vendorId());
        d.setVendorName(vendor.companyName());
        d.setWarehouseId(f.warehouseId());
        d.setInvoiceDate(f.invoiceDate());
        d.setDueDate(f.dueDate());
        d.setPaymentTerms(blank(f.paymentTerms()));
        d.setNotes(blank(f.notes()));
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        List<PurchaseInvoice.Line> lines = new ArrayList<>();
        int pos = 1;
        for (LineForm lf : f.lines()) {
            InventoryClient.Item item = inventory.item(lf.itemId());
            PurchaseInvoice.Line l = new PurchaseInvoice.Line();
            l.setPosition(pos++);
            l.setItemId(lf.itemId());
            l.setItemType(item.type());
            l.setItemName(item.name());
            l.setSku(item.sku());
            l.setQuantity(lf.quantity() == null ? BigDecimal.ONE : lf.quantity());
            l.setUnitPrice(lf.unitPrice() == null ? Money.nz(item.purchasePrice()) : lf.unitPrice());
            l.setDiscountPct(Money.nz(lf.discountPct()));
            l.setTaxName(item.taxName());
            l.setTaxRate(Money.nz(item.taxRate()));
            Money.Line m = Money.line(l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(), l.getTaxRate());
            l.setDiscount(m.discount());
            l.setTaxAmount(m.tax());
            l.setLineTotal(m.total());
            subtotal = subtotal.add(m.base());
            discount = discount.add(m.discount());
            tax = tax.add(m.tax());
            lines.add(l);
        }
        d.getLines().clear();
        d.getLines().addAll(lines);
        d.setSubtotal(subtotal);
        d.setDiscountTotal(discount);
        d.setTaxTotal(tax);
        d.setTotal(subtotal.subtract(discount).add(tax));
        d.setBalance(d.getTotal().subtract(d.getPaidAmount()));
    }

    // TODO(assumption): "today" (overdue) is the UTC date — confirm the business time zone.
    LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String like(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
