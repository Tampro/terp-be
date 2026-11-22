package com.terp.erp.commerce.sales;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.Money;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.sales.SalesDtos.DocumentDetail;
import com.terp.erp.commerce.sales.SalesDtos.DocumentForm;
import com.terp.erp.commerce.sales.SalesDtos.DocumentRow;
import com.terp.erp.commerce.sales.SalesDtos.LineForm;
import com.terp.erp.exception.ApiException;
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
 * Quotations, proposals and sales invoices (ERP manual 6.1, 6.2, 6.4). One service for the three kinds: the
 * controllers decide which kind and which permission. Only a DRAFT is edited or deleted.
 */
@Service
@RequiredArgsConstructor
public class SalesDocumentService {
    static final String STOCK_SOURCE = "SALES_INVOICE";
    /** The unified calendar (report-service) shows a month with the weeks around it. */
    public static final int MAX_CALENDAR_DAYS = 62;

    /** One invoice on the unified calendar. */
    public record CalendarEntry(String id, String number, String customerName, LocalDate docDate, LocalDate dueDate,
                                EDocumentStatus status, BigDecimal total, BigDecimal balance) {
    }

    private final SalesDocumentRepository documents;
    private final Numbering numbering;
    private final InventoryClient inventory;
    private final AccountingClient accounting;
    private final Clock clock;

    // ---------- reads ----------

    /** TODO(assumption): an invoice is placed on its due date (when it must be paid), drafts included. */
    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar(EDocumentKind kind, Long companyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(ECommerceError.SAL_020, MAX_CALENDAR_DAYS);
        }
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        return documents.dueBetween(company, kind, from, to).stream()
                .map(d -> new CalendarEntry(d.getId().toString(), d.getNumber(), d.getCustomerName(), d.getDocDate(),
                        d.getDueDate(), d.getStatus(), d.getTotal(), d.getBalance()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DocumentRow> search(EDocumentKind kind, Long companyId, Long customerId, Long warehouseId,
                                    LocalDate from, LocalDate to, String keyword, EDocumentStatus status,
                                    boolean overdue, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        LocalDate today = today();
        return documents.search(company, kind, customerId, warehouseId, from, to, like(keyword), status, overdue,
                today, pageable).map(d -> DocumentRow.of(d, today));
    }

    @Transactional(readOnly = true)
    public Map<EDocumentStatus, Long> statusCounts(EDocumentKind kind, Long companyId, Long customerId,
                                                   Long warehouseId, LocalDate from, LocalDate to, String keyword) {
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        Map<EDocumentStatus, Long> counts = new EnumMap<>(EDocumentStatus.class);
        for (EDocumentStatus s : statuses(kind)) {
            counts.put(s, 0L);
        }
        for (Object[] r : documents.countByStatus(company, kind, customerId, warehouseId, from, to, like(keyword))) {
            counts.put((EDocumentStatus) r[0], ((Number) r[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(EDocumentKind kind, Long id) {
        return DocumentDetail.of(require(kind, id), today());
    }

    // ---------- draft life ----------

    @Transactional
    public DocumentDetail create(EDocumentKind kind, DocumentForm f) {
        Long company = TenantGuard.resolveCompany(f.companyId(), ECommerceError.SAL_006);
        SalesDocument d = new SalesDocument();
        d.setCompanyId(company);
        d.setKind(kind);
        apply(d, f);
        d.setNumber(numbering.next(company, kind.prefix(), d.getDocDate()));
        return DocumentDetail.of(documents.save(d), today());
    }

    /** Full replacement of a draft; the number never changes. */
    @Transactional
    public DocumentDetail update(EDocumentKind kind, Long id, DocumentForm f) {
        SalesDocument d = requireDraft(kind, id);
        apply(d, f);
        return DocumentDetail.of(d, today());
    }

    @Transactional
    public void delete(EDocumentKind kind, Long id) {
        requireDraft(kind, id).setDeleted(true);
    }

    // ---------- quotation / proposal ----------

    @Transactional
    public DocumentDetail send(EDocumentKind kind, Long id) {
        SalesDocument d = requireDraft(kind, id);
        d.setStatus(EDocumentStatus.SENT);
        return DocumentDetail.of(d, today());
    }

    /** accept = true: ACCEPTED, false: REJECTED; only from SENT. */
    @Transactional
    public DocumentDetail decide(EDocumentKind kind, Long id, boolean accept) {
        SalesDocument d = require(kind, id);
        if (d.getStatus() != EDocumentStatus.SENT) {
            throw ApiException.badRequest(ECommerceError.SAL_003);
        }
        d.setStatus(accept ? EDocumentStatus.ACCEPTED : EDocumentStatus.REJECTED);
        return DocumentDetail.of(d, today());
    }

    /** A new DRAFT with the same customer, terms and lines; dated today, new number. */
    @Transactional
    public DocumentDetail duplicate(EDocumentKind kind, Long id) {
        SalesDocument src = require(kind, id);
        SalesDocument d = copy(src, kind);
        d.setNumber(numbering.next(d.getCompanyId(), kind.prefix(), d.getDocDate()));
        return DocumentDetail.of(documents.save(d), today());
    }

    /** Quotation only: a new DRAFT version of a sent, accepted or rejected quotation (ERP manual 6.4). */
    @Transactional
    public DocumentDetail newVersion(Long id) {
        SalesDocument src = require(EDocumentKind.QUOTATION, id);
        if (src.getStatus() == EDocumentStatus.DRAFT) {
            throw ApiException.badRequest(ECommerceError.SAL_017);
        }
        Long root = src.getVersionOfId() == null ? src.getId() : src.getVersionOfId();
        SalesDocument d = copy(src, EDocumentKind.QUOTATION);
        d.setVersionOfId(root);
        Integer max = documents.maxVersion(root);
        d.setVersionNo((max == null ? 1 : max) + 1);
        d.setNumber(numbering.next(d.getCompanyId(), EDocumentKind.QUOTATION.prefix(), d.getDocDate()));
        return DocumentDetail.of(documents.save(d), today());
    }

    /**
     * An accepted quotation or proposal becomes a DRAFT invoice with its lines, once. The source keeps its status
     * (ERP manual 6.1); a second conversion is refused (SAL_005). Service-wise when every line is a service.
     */
    @Transactional
    public DocumentDetail convert(EDocumentKind kind, Long id) {
        SalesDocument src = require(kind, id);
        if (src.getStatus() != EDocumentStatus.ACCEPTED) {
            throw ApiException.badRequest(ECommerceError.SAL_004);
        }
        if (src.getConvertedInvoiceId() != null) {
            String number = documents.findById(src.getConvertedInvoiceId()).map(SalesDocument::getNumber).orElse("");
            throw ApiException.badRequest(ECommerceError.SAL_005, number);
        }
        SalesDocument inv = copy(src, EDocumentKind.INVOICE);
        boolean allServices = src.getLines().stream().noneMatch(SalesDocumentLine::stocked);
        inv.setInvoiceType(allServices ? EInvoiceType.SERVICE : EInvoiceType.PRODUCT);
        if (allServices) {
            inv.setWarehouseId(null);
        }
        inv.setConvertedFromId(src.getId());
        inv.setNumber(numbering.next(inv.getCompanyId(), EDocumentKind.INVOICE.prefix(), inv.getDocDate()));
        documents.save(inv);
        src.setConvertedInvoiceId(inv.getId());
        return DocumentDetail.of(inv, today());
    }

    // ---------- invoice ----------

    /**
     * DRAFT → POSTED. A product-wise invoice takes its stocked lines out of its warehouse first (inventory-service,
     * all-or-nothing, idempotent on the invoice id, so a retry after a failure takes nothing twice). Not enough stock
     * → inventory-service's 400, and the invoice stays a draft. Journal entries come with accounting.
     */
    @Transactional
    public DocumentDetail post(Long id) {
        SalesDocument d = requireDraft(EDocumentKind.INVOICE, id);
        if (d.getInvoiceType() == EInvoiceType.PRODUCT) {
            if (d.getWarehouseId() == null) {
                throw ApiException.badRequest(ECommerceError.SAL_009);
            }
            List<InventoryClient.MoveLine> moves = d.getLines().stream().filter(SalesDocumentLine::stocked)
                    .map(l -> new InventoryClient.MoveLine(l.getItemId(), d.getWarehouseId(), l.getQuantity().negate()))
                    .toList();
            inventory.move(STOCK_SOURCE, d.getId(), moves);
        }
        d.setStatus(EDocumentStatus.POSTED);
        d.setPostedAt(Instant.now(clock));
        d.setBalance(d.getTotal().subtract(d.getPaidAmount()));
        return DocumentDetail.of(d, today());
    }

    /** For returns: a posted (or paid) invoice of the caller's company. */
    @Transactional(readOnly = true)
    public SalesDocument requirePostedInvoice(Long id) {
        SalesDocument d = require(EDocumentKind.INVOICE, id);
        if (d.getStatus() == EDocumentStatus.DRAFT) {
            throw ApiException.badRequest(ECommerceError.SAL_010);
        }
        return d;
    }

    // ---------- helpers ----------

    SalesDocument require(EDocumentKind kind, Long id) {
        SalesDocument d = documents.findByIdAndDeletedFalse(id)
                .filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), ECommerceError.SAL_001);
        return d;
    }

    private SalesDocument requireDraft(EDocumentKind kind, Long id) {
        SalesDocument d = require(kind, id);
        if (d.getStatus() != EDocumentStatus.DRAFT) {
            throw ApiException.badRequest(ECommerceError.SAL_002);
        }
        return d;
    }

    /** Header and lines from the form; item data and the customer come from their services with the caller's token. */
    private void apply(SalesDocument d, DocumentForm f) {
        if (f.dueDate().isBefore(f.docDate())) {
            throw ApiException.badRequest(ECommerceError.SAL_007);
        }
        EInvoiceType type = d.getKind() == EDocumentKind.INVOICE
                ? (f.invoiceType() == null ? EInvoiceType.PRODUCT : f.invoiceType()) : null;
        if (type == EInvoiceType.SERVICE && f.warehouseId() != null) {
            throw ApiException.badRequest(ECommerceError.SAL_019);
        }
        AccountingClient.Customer customer = accounting.customer(f.customerId());
        d.setCustomerId(f.customerId());
        d.setCustomerName(customer.companyName());
        d.setInvoiceType(type);
        d.setWarehouseId(f.warehouseId());
        d.setDocDate(f.docDate());
        d.setDueDate(f.dueDate());
        d.setPaymentTerms(blank(f.paymentTerms()));
        d.setNotes(blank(f.notes()));
        List<SalesDocumentLine> lines = new ArrayList<>();
        int pos = 1;
        for (LineForm lf : f.lines()) {
            InventoryClient.Item item = inventory.item(lf.itemId());
            if (type == EInvoiceType.SERVICE && item.stocked()) {
                throw ApiException.badRequest(ECommerceError.SAL_008, item.sku() == null ? item.name() : item.sku());
            }
            BigDecimal qty = lf.quantity() == null ? BigDecimal.ONE : lf.quantity();
            if (type == EInvoiceType.SERVICE && qty.compareTo(BigDecimal.ONE) != 0) {
                throw ApiException.badRequest(ECommerceError.SAL_019);
            }
            SalesDocumentLine l = new SalesDocumentLine();
            l.setPosition(pos++);
            l.setItemId(lf.itemId());
            l.setItemType(item.type());
            l.setItemName(item.name());
            l.setSku(item.sku());
            l.setQuantity(qty);
            l.setUnitPrice(lf.unitPrice() == null ? Money.nz(item.salePrice()) : lf.unitPrice());
            l.setDiscountPct(Money.nz(lf.discountPct()));
            l.setTaxName(item.taxName());
            l.setTaxRate(Money.nz(item.taxRate()));
            lines.add(l);
        }
        replaceLines(d, lines);
    }

    /** Recomputes every line and the totals; replaces the lines in place (orphans removed). */
    static void replaceLines(SalesDocument d, List<SalesDocumentLine> lines) {
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (SalesDocumentLine l : lines) {
            Money.Line m = Money.line(l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(), l.getTaxRate());
            l.setDiscount(m.discount());
            l.setTaxAmount(m.tax());
            l.setLineTotal(m.total());
            subtotal = subtotal.add(m.base());
            discount = discount.add(m.discount());
            tax = tax.add(m.tax());
        }
        d.getLines().clear();
        d.getLines().addAll(lines);
        d.setSubtotal(subtotal);
        d.setDiscountTotal(discount);
        d.setTaxTotal(tax);
        d.setTotal(subtotal.subtract(discount).add(tax));
        d.setBalance(d.getKind() == EDocumentKind.INVOICE ? d.getTotal().subtract(d.getPaidAmount()) : BigDecimal.ZERO);
    }

    /** A DRAFT copy (header and lines as they are, no new item lookup), dated today. */
    private SalesDocument copy(SalesDocument src, EDocumentKind kind) {
        SalesDocument d = new SalesDocument();
        d.setCompanyId(src.getCompanyId());
        d.setKind(kind);
        d.setInvoiceType(kind == EDocumentKind.INVOICE ? src.getInvoiceType() : null);
        d.setCustomerId(src.getCustomerId());
        d.setCustomerName(src.getCustomerName());
        d.setWarehouseId(src.getWarehouseId());
        LocalDate today = today();
        long days = java.time.temporal.ChronoUnit.DAYS.between(src.getDocDate(), src.getDueDate());
        d.setDocDate(today);
        d.setDueDate(today.plusDays(Math.max(0, days)));
        d.setPaymentTerms(src.getPaymentTerms());
        d.setNotes(src.getNotes());
        List<SalesDocumentLine> lines = new ArrayList<>();
        for (SalesDocumentLine s : src.getLines()) {
            SalesDocumentLine l = new SalesDocumentLine();
            l.setPosition(s.getPosition());
            l.setItemId(s.getItemId());
            l.setItemType(s.getItemType());
            l.setItemName(s.getItemName());
            l.setSku(s.getSku());
            l.setQuantity(s.getQuantity());
            l.setUnitPrice(s.getUnitPrice());
            l.setDiscountPct(s.getDiscountPct());
            l.setTaxName(s.getTaxName());
            l.setTaxRate(s.getTaxRate());
            lines.add(l);
        }
        replaceLines(d, lines);
        return d;
    }

    private static List<EDocumentStatus> statuses(EDocumentKind kind) {
        return kind == EDocumentKind.INVOICE
                ? List.of(EDocumentStatus.DRAFT, EDocumentStatus.POSTED, EDocumentStatus.PARTIAL, EDocumentStatus.PAID)
                : List.of(EDocumentStatus.DRAFT, EDocumentStatus.SENT, EDocumentStatus.ACCEPTED,
                EDocumentStatus.REJECTED);
    }

    // TODO(assumption): "today" (overdue, expiry, duplicate dates) is the UTC date — confirm the business time zone.
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
