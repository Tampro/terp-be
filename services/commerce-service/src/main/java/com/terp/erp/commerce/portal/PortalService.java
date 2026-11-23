package com.terp.erp.commerce.portal;

import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.portal.PortalDtos.PurchaseDetail;
import com.terp.erp.commerce.portal.PortalDtos.PurchaseRow;
import com.terp.erp.commerce.portal.PortalDtos.ReturnDetail;
import com.terp.erp.commerce.portal.PortalDtos.ReturnRow;
import com.terp.erp.commerce.portal.PortalDtos.SalesDetail;
import com.terp.erp.commerce.portal.PortalDtos.SalesRow;
import com.terp.erp.commerce.portal.PortalPartyResolver.Mine;
import com.terp.erp.commerce.purchase.PurchaseInvoice;
import com.terp.erp.commerce.purchase.PurchaseInvoiceRepository;
import com.terp.erp.commerce.purchase.PurchaseReturn;
import com.terp.erp.commerce.purchase.PurchaseReturnRepository;
import com.terp.erp.commerce.sales.EDocumentKind;
import com.terp.erp.commerce.sales.EDocumentStatus;
import com.terp.erp.commerce.sales.SalesDocument;
import com.terp.erp.commerce.sales.SalesDocumentRepository;
import com.terp.erp.commerce.sales.SalesDocumentService;
import com.terp.erp.commerce.salesreturn.SalesReturn;
import com.terp.erp.commerce.salesreturn.SalesReturnRepository;
import com.terp.erp.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Client and vendor portal over commerce documents. The party is always the one accounting links to the caller
 * (never an id from the request); another customer's / vendor's document, a draft, or another company's answers
 * 404 like a missing one. TODO(assumption): customers and vendors never see drafts (DRAFT is internal work).
 */
@Service
@RequiredArgsConstructor
public class PortalService {
    private final PortalPartyResolver party;
    private final SalesDocumentRepository documents;
    private final SalesReturnRepository salesReturns;
    private final PurchaseInvoiceRepository purchaseInvoices;
    private final PurchaseReturnRepository purchaseReturns;
    private final SalesDocumentService sales;
    private final Clock clock;

    // ---------- client ----------

    @Transactional(readOnly = true)
    public Page<SalesRow> clientDocuments(EDocumentKind kind, Pageable pageable) {
        Mine me = party.customer();
        LocalDate today = today();
        return documents.forCustomer(me.companyId(), kind, me.partyId(), pageable).map(d -> SalesRow.of(d, today));
    }

    @Transactional(readOnly = true)
    public SalesDetail clientDocument(EDocumentKind kind, Long id) {
        return SalesDetail.of(mySalesDocument(kind, id), today());
    }

    /** Printable copy of {@link #clientDocument}; the same checks, built from the portal DTO only. */
    @Transactional(readOnly = true)
    public Pdf clientDocumentPdf(EDocumentKind kind, Long id) {
        SalesDetail d = clientDocument(kind, id);
        return new Pdf(PortalPdf.sales(d, party.customer().name()), d.document().number() + ".pdf");
    }

    /** A generated file and the name it is downloaded under. */
    public record Pdf(byte[] bytes, String fileName) {
    }

    /**
     * The customer accepts or rejects a SENT quotation / proposal (SAL_003 otherwise), through the same rule as
     * staff; updated_by records the customer's own account (no separate actor column exists).
     */
    @Transactional
    public SalesDetail decide(EDocumentKind kind, Long id, boolean accept) {
        SalesDocument d = mySalesDocument(kind, id);
        sales.decide(d, accept);
        return SalesDetail.of(d, today());
    }

    @Transactional(readOnly = true)
    public Page<ReturnRow> clientReturns(Pageable pageable) {
        Mine me = party.customer();
        return salesReturns.forCustomer(me.companyId(), me.partyId(), pageable).map(ReturnRow::of);
    }

    @Transactional(readOnly = true)
    public ReturnDetail clientReturn(Long id) {
        Mine me = party.customer();
        SalesReturn r = salesReturns.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), me.companyId()))
                .filter(x -> Objects.equals(x.getCustomerId(), me.partyId()))
                .filter(x -> x.getStatus() != SalesReturn.EStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_012));
        return ReturnDetail.of(r);
    }

    @Transactional(readOnly = true)
    public Pdf clientReturnPdf(Long id) {
        ReturnDetail d = clientReturn(id);
        return new Pdf(PortalPdf.salesReturn(d, party.customer().name()), d.ret().number() + ".pdf");
    }

    // ---------- vendor ----------

    @Transactional(readOnly = true)
    public Page<PurchaseRow> vendorInvoices(Pageable pageable) {
        Mine me = party.vendor();
        LocalDate today = today();
        return purchaseInvoices.forVendor(me.companyId(), me.partyId(), pageable).map(d -> PurchaseRow.of(d, today));
    }

    @Transactional(readOnly = true)
    public PurchaseDetail vendorInvoice(Long id) {
        Mine me = party.vendor();
        PurchaseInvoice d = purchaseInvoices.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), me.companyId()))
                .filter(x -> Objects.equals(x.getVendorId(), me.partyId()))
                .filter(x -> x.getStatus() != PurchaseInvoice.EStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound(EPurchaseError.PUR_001));
        return PurchaseDetail.of(d, today());
    }

    @Transactional(readOnly = true)
    public Page<ReturnRow> vendorReturns(Pageable pageable) {
        Mine me = party.vendor();
        return purchaseReturns.forVendor(me.companyId(), me.partyId(), pageable).map(ReturnRow::of);
    }

    @Transactional(readOnly = true)
    public ReturnDetail vendorReturn(Long id) {
        Mine me = party.vendor();
        PurchaseReturn r = purchaseReturns.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), me.companyId()))
                .filter(x -> Objects.equals(x.getVendorId(), me.partyId()))
                .filter(x -> x.getStatus() != PurchaseReturn.EStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound(EPurchaseError.PUR_008));
        return ReturnDetail.of(r);
    }

    // ---------- helpers ----------

    private SalesDocument mySalesDocument(EDocumentKind kind, Long id) {
        Mine me = party.customer();
        return documents.findByIdAndDeletedFalse(id)
                .filter(x -> x.getKind() == kind)
                .filter(x -> Objects.equals(x.getCompanyId(), me.companyId()))
                .filter(x -> Objects.equals(x.getCustomerId(), me.partyId()))
                .filter(x -> x.getStatus() != EDocumentStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_001));
    }

    // TODO(assumption): "today" (overdue, expiry) is the UTC date, as in SalesDocumentService.
    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
