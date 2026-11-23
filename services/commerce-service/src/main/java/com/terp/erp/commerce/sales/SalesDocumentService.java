package com.terp.erp.commerce.sales;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.EJournalStatus;
import com.terp.erp.commerce.common.Money;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.common.Ownership;
import com.terp.erp.commerce.common.Ownership.Access;
import com.terp.erp.commerce.common.Postings;
import com.terp.erp.commerce.sales.SalesDtos.DocumentDetail;
import com.terp.erp.commerce.sales.SalesDtos.DocumentForm;
import com.terp.erp.commerce.sales.SalesDtos.DocumentRow;
import com.terp.erp.commerce.sales.SalesDtos.LineForm;
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
 * Quotations, proposals and sales invoices (ERP manual 6.1, 6.2, 6.4). One service for the three kinds: the
 * controllers decide which kind and which permission. Only a DRAFT is edited or deleted.
 */
@Service
@RequiredArgsConstructor
public class SalesDocumentService {
    static final String STOCK_SOURCE = "SALES_INVOICE";
    static final String EVENT_POSTED = "sales.invoice.posted";
    static final String EVENT_INVOICE_STATUS = "sales.invoice.status_changed";
    static final String EVENT_PROPOSAL_CREATED = "sales.proposal.created";
    static final String EVENT_PROPOSAL_STATUS = "sales.proposal.status_changed";
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
    private final OutboxPublisher outbox;

    // ---------- reads ----------

    /** TODO(assumption): an invoice is placed on its due date (when it must be paid), drafts included. */
    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar(EDocumentKind kind, Long companyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(ECommerceError.SAL_020, MAX_CALENDAR_DAYS);
        }
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        return documents.dueBetween(company, kind, from, to, owner(kind, Access.VIEW)).stream()
                .map(d -> new CalendarEntry(d.getId().toString(), d.getNumber(), d.getCustomerName(), d.getDocDate(),
                        d.getDueDate(), d.getStatus(), d.getTotal(), d.getBalance()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DocumentRow> search(EDocumentKind kind, Long companyId, Long customerId, Long warehouseId,
                                    LocalDate from, LocalDate to, String keyword, EDocumentStatus status,
                                    boolean overdue, EJournalStatus journalStatus, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        String owner = owner(kind, Access.VIEW);
        LocalDate today = today();
        return documents.search(company, kind, customerId, warehouseId, from, to, like(keyword), status, overdue,
                journalStatus, today, owner, pageable).map(d -> DocumentRow.of(d, today));
    }

    @Transactional(readOnly = true)
    public Map<EDocumentStatus, Long> statusCounts(EDocumentKind kind, Long companyId, Long customerId,
                                                   Long warehouseId, LocalDate from, LocalDate to, String keyword) {
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        String owner = owner(kind, Access.VIEW);
        Map<EDocumentStatus, Long> counts = new EnumMap<>(EDocumentStatus.class);
        for (EDocumentStatus s : statuses(kind)) {
            counts.put(s, 0L);
        }
        for (Object[] r : documents.countByStatus(company, kind, customerId, warehouseId, from, to, like(keyword),
                owner)) {
            counts.put((EDocumentStatus) r[0], ((Number) r[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public DocumentDetail get(EDocumentKind kind, Long id) {
        return DocumentDetail.of(require(kind, id, Access.VIEW), today());
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
        SalesDocument saved = documents.save(d);
        proposalCreated(saved);
        return DocumentDetail.of(saved, today());
    }

    /** Full replacement of a draft; the number never changes. */
    @Transactional
    public DocumentDetail update(EDocumentKind kind, Long id, DocumentForm f) {
        SalesDocument d = requireDraft(kind, id, Access.MANAGE);
        apply(d, f);
        return DocumentDetail.of(d, today());
    }

    @Transactional
    public void delete(EDocumentKind kind, Long id) {
        requireDraft(kind, id, Access.MANAGE).setDeleted(true);
    }

    // ---------- quotation / proposal ----------

    @Transactional
    public DocumentDetail send(EDocumentKind kind, Long id) {
        SalesDocument d = requireDraft(kind, id, Access.MANAGE);
        d.setStatus(EDocumentStatus.SENT);
        proposalStatusChanged(d, EDocumentStatus.DRAFT);
        return DocumentDetail.of(d, today());
    }

    /** accept = true: ACCEPTED, false: REJECTED; only from SENT. */
    @Transactional
    public DocumentDetail decide(EDocumentKind kind, Long id, boolean accept) {
        return decide(require(kind, id, Access.MANAGE), accept);
    }

    /**
     * The decision itself, on a document already checked by the caller (staff above, client portal for its own
     * customer). updated_by records who decided: the customer's own account when done from the portal.
     */
    @Transactional
    public DocumentDetail decide(SalesDocument d, boolean accept) {
        if (d.getStatus() != EDocumentStatus.SENT) {
            throw ApiException.badRequest(ECommerceError.SAL_003);
        }
        d.setStatus(accept ? EDocumentStatus.ACCEPTED : EDocumentStatus.REJECTED);
        proposalStatusChanged(d, EDocumentStatus.SENT);
        return DocumentDetail.of(d, today());
    }

    /** A new DRAFT with the same customer, terms and lines; dated today, new number. */
    @Transactional
    public DocumentDetail duplicate(EDocumentKind kind, Long id) {
        SalesDocument src = require(kind, id, Access.MANAGE);
        SalesDocument d = copy(src, kind);
        d.setNumber(numbering.next(d.getCompanyId(), kind.prefix(), d.getDocDate()));
        SalesDocument saved = documents.save(d);
        proposalCreated(saved);
        return DocumentDetail.of(saved, today());
    }

    /** Quotation only: a new DRAFT version of a sent, accepted or rejected quotation (ERP manual 6.4). */
    @Transactional
    public DocumentDetail newVersion(Long id) {
        SalesDocument src = require(EDocumentKind.QUOTATION, id, Access.MANAGE);
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
        SalesDocument src = require(kind, id, Access.MANAGE);
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
     * DRAFT → POSTED. A product-wise invoice takes its stocked lines out of its warehouse (inventory-service,
     * all-or-nothing, idempotent on the invoice id, so a retry after a failure takes nothing twice) and the invoice
     * journal (Dr 131 / Cr 5111, 5113, 33311; Dr 632 / Cr 156 at cost) is requested from accounting-service through
     * the outbox, in the order of {@link Postings}: a closed year (ACC_070) or not enough stock is relayed and the
     * invoice stays a draft. The journal itself appears asynchronously: journalStatus PENDING → POSTED | FAILED.
     * TODO(assumption): cost = the item's purchase price when posted (costing method is an open ARQ question).
     */
    @Transactional
    public DocumentDetail post(Long id) {
        SalesDocument d = requireDraft(EDocumentKind.INVOICE, id, Access.POST);
        boolean product = d.getInvoiceType() == EInvoiceType.PRODUCT;
        if (product && d.getWarehouseId() == null) {
            throw ApiException.badRequest(ECommerceError.SAL_009);
        }
        for (SalesDocumentLine l : d.getLines()) {
            if (product && l.stocked()) {
                l.setUnitCost(Money.nz(inventory.item(l.getItemId()).purchasePrice()));
            }
        }
        d.setJournalStatus(Postings.run(accounting, outbox, d.getCompanyId(), posting(d), () -> {
            if (product) {
                inventory.move(STOCK_SOURCE, d.getId(), d.getLines().stream().filter(SalesDocumentLine::stocked)
                        .map(l -> new InventoryClient.MoveLine(l.getItemId(), d.getWarehouseId(),
                                l.getQuantity().negate()))
                        .toList());
            }
        }));
        d.setJournalErrorCode(null);
        d.setStatus(EDocumentStatus.POSTED);
        d.setPostedAt(Instant.now(clock));
        d.setBalance(d.getTotal().subtract(d.getPaidAmount()));
        // Notifications (webhook NEW_SALES_INVOICE, email INVOICE_SENT). TODO(assumption): no recipientEmail — the
        // customer's address lives in accounting-service; the email is SKIPPED (NO_RECIPIENT) until it is added.
        outbox.publish(d.getCompanyId(), EVENT_POSTED, "SALES_INVOICE", d.getId(), Payload.of(
                "invoiceId", d.getId(), "number", d.getNumber(), "invoiceType", d.getInvoiceType(),
                "customerId", d.getCustomerId(), "customerName", d.getCustomerName(), "docDate", d.getDocDate(),
                "dueDate", d.getDueDate(), "total", d.getTotal(), "balance", d.getBalance(),
                "syncToGoogleCalendar", d.isSyncToGoogleCalendar()));
        outbox.publish(d.getCompanyId(), EVENT_INVOICE_STATUS, "SALES_INVOICE", d.getId(),
                invoiceStatusPayload(d, EDocumentStatus.DRAFT));
        return DocumentDetail.of(d, today());
    }

    /**
     * Re-post of a journal that accounting refused after the invoice was posted (journalStatus FAILED, e.g. the year
     * was closed in between): validated again synchronously, then requested again; journalStatus → PENDING. The
     * amounts are those of the posting (cost from the unit cost kept on the lines). Stock is not moved again.
     */
    @Transactional
    public DocumentDetail retryJournal(Long id) {
        SalesDocument d = require(EDocumentKind.INVOICE, id, Access.POST);
        if (d.getJournalStatus() != EJournalStatus.FAILED) {
            throw ApiException.badRequest(ECommerceError.SAL_025);
        }
        Postings.retry(accounting, outbox, d.getCompanyId(), posting(d));
        d.setJournalStatus(EJournalStatus.PENDING);
        d.setJournalErrorCode(null);
        return DocumentDetail.of(d, today());
    }

    /** Amounts by role of a product / service invoice; cost from the unit cost the lines took when posted. */
    static AccountingClient.Posting posting(SalesDocument d) {
        boolean product = d.getInvoiceType() == EInvoiceType.PRODUCT;
        BigDecimal goods = BigDecimal.ZERO, service = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (SalesDocumentLine l : d.getLines()) {
            BigDecimal net = l.getLineTotal().subtract(l.getTaxAmount());
            if (product && l.stocked()) {
                goods = goods.add(net);
                cost = cost.add(Money.round(Money.nz(l.getUnitCost()).multiply(l.getQuantity())));
            } else {
                service = service.add(net);
            }
        }
        return new AccountingClient.Posting(STOCK_SOURCE, d.getId(), d.getDocDate(), d.getNumber(),
                "Sales invoice " + d.getNumber() + " - " + d.getCustomerName(), goods, service, d.getTaxTotal(), cost);
    }

    /**
     * For returns: a posted (or paid) invoice of the caller's company; a caller holding only *_OWN sales codes reaches
     * only invoices they created (the Access.POST rule).
     */
    @Transactional(readOnly = true)
    public SalesDocument requirePostedInvoice(Long id) {
        SalesDocument d = require(EDocumentKind.INVOICE, id, Access.POST);
        if (d.getStatus() == EDocumentStatus.DRAFT) {
            throw ApiException.badRequest(ECommerceError.SAL_010);
        }
        return d;
    }

    /**
     * For return create / edit: the posted invoice row locked until the transaction ends, so two returns of one
     * invoice are validated one after the other and cannot both take the last returnable quantity.
     */
    @Transactional
    public SalesDocument lockPostedInvoice(Long id) {
        SalesDocument d = documents.lockById(id).filter(x -> x.getKind() == EDocumentKind.INVOICE)
                .orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), ECommerceError.SAL_001);
        Ownership.check(owner(EDocumentKind.INVOICE, Access.POST), d.getCreatedBy(), ECommerceError.SAL_001);
        if (d.getStatus() == EDocumentStatus.DRAFT) {
            throw ApiException.badRequest(ECommerceError.SAL_010);
        }
        return d;
    }

    // ---------- events ----------

    /** Payload of sales.invoice.status_changed (webhook SALES_INVOICE_STATUS_UPDATED); no recipient. */
    static java.util.Map<String, Object> invoiceStatusPayload(SalesDocument d, EDocumentStatus from) {
        return Payload.of("invoiceId", d.getId(), "title", d.getNumber(), "number", d.getNumber(),
                "customerId", d.getCustomerId(), "customerName", d.getCustomerName(), "fromStatus", from,
                "status", d.getStatus(), "total", d.getTotal(), "balance", d.getBalance());
    }

    /** Webhook NEW_SALES_PROPOSAL; quotations and invoices have no "created" event. */
    private void proposalCreated(SalesDocument d) {
        if (d.getKind() != EDocumentKind.PROPOSAL) {
            return;
        }
        outbox.publish(d.getCompanyId(), EVENT_PROPOSAL_CREATED, "SALES_PROPOSAL", d.getId(), Payload.of(
                "proposalId", d.getId(), "title", d.getNumber(), "number", d.getNumber(),
                "customerId", d.getCustomerId(), "customerName", d.getCustomerName(), "docDate", d.getDocDate(),
                "dueDate", d.getDueDate(), "total", d.getTotal(), "status", d.getStatus()));
    }

    /**
     * Webhook SALES_PROPOSAL_STATUS_UPDATED + email PROPOSAL_STATUS_UPDATED. TODO(assumption): the email should go
     * to the proposal's creator, but created_by is a Keycloak sub, not an IAM user id: no recipientUserIds yet
     * (createdBy is carried so notification-service could resolve it later).
     */
    private void proposalStatusChanged(SalesDocument d, EDocumentStatus from) {
        if (d.getKind() != EDocumentKind.PROPOSAL) {
            return;
        }
        outbox.publish(d.getCompanyId(), EVENT_PROPOSAL_STATUS, "SALES_PROPOSAL", d.getId(), Payload.of(
                "proposalId", d.getId(), "title", d.getNumber(), "number", d.getNumber(),
                "customerId", d.getCustomerId(), "customerName", d.getCustomerName(), "fromStatus", from,
                "status", d.getStatus(), "total", d.getTotal(), "createdBy", d.getCreatedBy()));
    }

    // ---------- helpers ----------

    SalesDocument require(EDocumentKind kind, Long id, Access access) {
        SalesDocument d = documents.findByIdAndDeletedFalse(id)
                .filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), ECommerceError.SAL_001);
        Ownership.check(owner(kind, access), d.getCreatedBy(), ECommerceError.SAL_001);
        return d;
    }

    /** null = every document of the company; else the caller's sub (Manage Own). Quotations have their own codes. */
    static String owner(EDocumentKind kind, Access access) {
        return Ownership.owner(kind == EDocumentKind.QUOTATION ? "QUOTATION" : "SALES", access);
    }

    private SalesDocument requireDraft(EDocumentKind kind, Long id, Access access) {
        SalesDocument d = require(kind, id, access);
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
        if (f.warehouseId() != null) {
            // another company's or a missing warehouse: inventory-service's own 404 (relayed)
            if (!inventory.warehouse(f.warehouseId()).active()) {
                throw ApiException.badRequest(ECommerceError.SAL_024);
            }
        }
        AccountingClient.Customer customer = accounting.customer(f.customerId());
        d.setCustomerId(f.customerId());
        d.setCustomerName(customer.companyName());
        d.setCustomerEmail(customer.email());
        d.setInvoiceType(type);
        d.setWarehouseId(f.warehouseId());
        d.setDocDate(f.docDate());
        d.setDueDate(f.dueDate());
        d.setPaymentTerms(blank(f.paymentTerms()));
        d.setNotes(blank(f.notes()));
        d.setSyncToGoogleCalendar(d.getKind() == EDocumentKind.INVOICE && Boolean.TRUE.equals(f.syncToGoogleCalendar()));
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
        d.setCustomerEmail(src.getCustomerEmail());
        d.setWarehouseId(src.getWarehouseId());
        LocalDate today = today();
        long days = java.time.temporal.ChronoUnit.DAYS.between(src.getDocDate(), src.getDueDate());
        d.setDocDate(today);
        d.setDueDate(today.plusDays(Math.max(0, days)));
        d.setPaymentTerms(src.getPaymentTerms());
        d.setNotes(src.getNotes());
        // TODO(assumption): a converted / copied invoice starts without calendar sync; the user ticks it on the draft.
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
