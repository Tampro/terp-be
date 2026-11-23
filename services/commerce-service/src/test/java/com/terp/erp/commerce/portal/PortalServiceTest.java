package com.terp.erp.commerce.portal;

import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.client.RemoteApiException;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.Numbering;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalServiceTest {
    private static final Long COMPANY = 10L, MY_CUSTOMER = 5L, MY_VENDOR = 3L;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private final PortalPartyResolver party = mock(PortalPartyResolver.class);
    private final SalesDocumentRepository documents = mock(SalesDocumentRepository.class);
    private final SalesReturnRepository salesReturns = mock(SalesReturnRepository.class);
    private final PurchaseInvoiceRepository purchaseInvoices = mock(PurchaseInvoiceRepository.class);
    private final PurchaseReturnRepository purchaseReturns = mock(PurchaseReturnRepository.class);
    private final SalesDocumentService sales = new SalesDocumentService(documents, mock(Numbering.class),
            mock(InventoryClient.class), mock(AccountingClient.class), clock,
            mock(com.terp.erp.outbox.OutboxPublisher.class));
    private final PortalService service = new PortalService(party, documents, salesReturns, purchaseInvoices,
            purchaseReturns, sales, clock);

    @BeforeEach
    void setUp() {
        TestLogin.as(COMPANY, "PORTAL_CLIENT", "PORTAL_VENDOR");
        when(party.customer()).thenReturn(new Mine(COMPANY, MY_CUSTOMER, "Test Customer"));
        when(party.vendor()).thenReturn(new Mine(COMPANY, MY_VENDOR, "Test Vendor"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static SalesDocument doc(long id, EDocumentKind kind, EDocumentStatus status, Long company, Long customer) {
        SalesDocument d = new SalesDocument();
        d.setId(id);
        d.setCompanyId(company);
        d.setKind(kind);
        d.setStatus(status);
        d.setNumber("QT-" + id);
        d.setCustomerId(customer);
        d.setCustomerName("Test Customer");
        d.setDocDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 12, 1));
        d.setNotes("internal note");
        d.setTotal(BigDecimal.TEN);
        d.setCreatedBy("kc-staff");
        return d;
    }

    @Test
    void listsOnlyMyCustomersDocumentsThroughTheNonDraftQuery() {
        when(documents.forCustomer(COMPANY, EDocumentKind.INVOICE, MY_CUSTOMER, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(doc(1L, EDocumentKind.INVOICE, EDocumentStatus.POSTED, COMPANY,
                        MY_CUSTOMER))));
        var page = service.clientDocuments(EDocumentKind.INVOICE, Pageable.unpaged());
        assertThat(page.getContent()).extracting(PortalDtos.SalesRow::id).containsExactly("1");
    }

    @Test
    void anotherCustomersADraftOrAnotherCompanysDocumentAnswers404() {
        when(documents.findByIdAndDeletedFalse(2L))
                .thenReturn(Optional.of(doc(2L, EDocumentKind.QUOTATION, EDocumentStatus.SENT, COMPANY, 99L)));
        when(documents.findByIdAndDeletedFalse(3L))
                .thenReturn(Optional.of(doc(3L, EDocumentKind.QUOTATION, EDocumentStatus.DRAFT, COMPANY, MY_CUSTOMER)));
        when(documents.findByIdAndDeletedFalse(4L))
                .thenReturn(Optional.of(doc(4L, EDocumentKind.QUOTATION, EDocumentStatus.SENT, 20L, MY_CUSTOMER)));
        when(documents.findByIdAndDeletedFalse(5L))
                .thenReturn(Optional.of(doc(5L, EDocumentKind.PROPOSAL, EDocumentStatus.SENT, COMPANY, MY_CUSTOMER)));
        for (long id : new long[]{2L, 3L, 4L, 5L}) {
            assertThatThrownBy(() -> service.clientDocument(EDocumentKind.QUOTATION, id))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
        }
        assertThatThrownBy(() -> service.decide(EDocumentKind.QUOTATION, 2L, true))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
    }

    @Test
    void detailHidesInternalFieldsAndPdfIsGenerated() {
        when(documents.findByIdAndDeletedFalse(6L))
                .thenReturn(Optional.of(doc(6L, EDocumentKind.QUOTATION, EDocumentStatus.SENT, COMPANY, MY_CUSTOMER)));
        var d = service.clientDocument(EDocumentKind.QUOTATION, 6L);
        assertThat(d.toString()).doesNotContain("internal note").doesNotContain("kc-staff");
        assertThat(d.document().balance()).isNull();
        var pdf = service.clientDocumentPdf(EDocumentKind.QUOTATION, 6L);
        assertThat(new String(pdf.bytes(), 0, 4)).isEqualTo("%PDF");
        assertThat(pdf.fileName()).isEqualTo("QT-6.pdf");
    }

    @Test
    void acceptOnlyWhenSent() {
        SalesDocument sent = doc(7L, EDocumentKind.QUOTATION, EDocumentStatus.SENT, COMPANY, MY_CUSTOMER);
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(sent));
        assertThat(service.decide(EDocumentKind.QUOTATION, 7L, true).document().status())
                .isEqualTo(EDocumentStatus.ACCEPTED);
        assertThatThrownBy(() -> service.decide(EDocumentKind.QUOTATION, 7L, false))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_003);

        SalesDocument proposal = doc(8L, EDocumentKind.PROPOSAL, EDocumentStatus.SENT, COMPANY, MY_CUSTOMER);
        when(documents.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(proposal));
        assertThat(service.decide(EDocumentKind.PROPOSAL, 8L, false).document().status())
                .isEqualTo(EDocumentStatus.REJECTED);
    }

    @Test
    void clientReturnsOnlyMineAndNotDraft() {
        SalesReturn mine = salesReturn(70L, MY_CUSTOMER, SalesReturn.EStatus.COMPLETED);
        when(salesReturns.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(mine));
        when(salesReturns.findByIdAndDeletedFalse(71L))
                .thenReturn(Optional.of(salesReturn(71L, 99L, SalesReturn.EStatus.COMPLETED)));
        when(salesReturns.findByIdAndDeletedFalse(72L))
                .thenReturn(Optional.of(salesReturn(72L, MY_CUSTOMER, SalesReturn.EStatus.DRAFT)));
        when(salesReturns.forCustomer(COMPANY, MY_CUSTOMER, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(mine)));
        assertThat(service.clientReturns(Pageable.unpaged()).getContent()).hasSize(1);
        assertThat(service.clientReturn(70L).ret().id()).isEqualTo("70");
        assertThat(new String(service.clientReturnPdf(70L).bytes(), 0, 4)).isEqualTo("%PDF");
        for (long id : new long[]{71L, 72L}) {
            assertThatThrownBy(() -> service.clientReturn(id))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_012);
        }
    }

    @Test
    void vendorSeesOnlyMyVendorsNonDraftPurchaseDocuments() {
        when(purchaseInvoices.findByIdAndDeletedFalse(30L))
                .thenReturn(Optional.of(purchaseInvoice(30L, MY_VENDOR, PurchaseInvoice.EStatus.POSTED)));
        when(purchaseInvoices.findByIdAndDeletedFalse(31L))
                .thenReturn(Optional.of(purchaseInvoice(31L, 99L, PurchaseInvoice.EStatus.POSTED)));
        when(purchaseInvoices.findByIdAndDeletedFalse(32L))
                .thenReturn(Optional.of(purchaseInvoice(32L, MY_VENDOR, PurchaseInvoice.EStatus.DRAFT)));
        when(purchaseInvoices.forVendor(COMPANY, MY_VENDOR, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(purchaseInvoice(30L, MY_VENDOR, PurchaseInvoice.EStatus.POSTED))));
        assertThat(service.vendorInvoices(Pageable.unpaged()).getContent()).extracting(PortalDtos.PurchaseRow::id)
                .containsExactly("30");
        assertThat(service.vendorInvoice(30L).invoice().id()).isEqualTo("30");
        for (long id : new long[]{31L, 32L}) {
            assertThatThrownBy(() -> service.vendorInvoice(id))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
        }

        when(purchaseReturns.findByIdAndDeletedFalse(80L))
                .thenReturn(Optional.of(purchaseReturn(80L, MY_VENDOR, PurchaseReturn.EStatus.APPROVED)));
        when(purchaseReturns.findByIdAndDeletedFalse(81L))
                .thenReturn(Optional.of(purchaseReturn(81L, 99L, PurchaseReturn.EStatus.APPROVED)));
        when(purchaseReturns.findByIdAndDeletedFalse(82L))
                .thenReturn(Optional.of(purchaseReturn(82L, MY_VENDOR, PurchaseReturn.EStatus.DRAFT)));
        when(purchaseReturns.forVendor(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.vendorReturns(Pageable.unpaged()).getContent()).isEmpty();
        assertThat(service.vendorReturn(80L).ret().id()).isEqualTo("80");
        for (long id : new long[]{81L, 82L}) {
            assertThatThrownBy(() -> service.vendorReturn(id))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_008);
        }
    }

    @Test
    void notLinkedIsRelayedAs404() {
        when(party.customer()).thenThrow(new RemoteApiException(HttpStatus.NOT_FOUND, "ACC_098", "not linked"));
        assertThatThrownBy(() -> service.clientDocuments(EDocumentKind.QUOTATION, Pageable.unpaged()))
                .isInstanceOf(RemoteApiException.class);
        when(party.vendor()).thenThrow(ApiException.notFound(EPurchaseError.PUR_018));
        assertThatThrownBy(() -> service.vendorInvoices(Pageable.unpaged()))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_018);
    }

    private static SalesReturn salesReturn(long id, Long customer, SalesReturn.EStatus status) {
        SalesReturn r = new SalesReturn();
        r.setId(id);
        r.setCompanyId(COMPANY);
        r.setNumber("SR-" + id);
        r.setInvoiceId(1L);
        r.setCustomerId(customer);
        r.setReturnDate(LocalDate.of(2026, 11, 20));
        r.setStatus(status);
        return r;
    }

    private static PurchaseInvoice purchaseInvoice(long id, Long vendor, PurchaseInvoice.EStatus status) {
        PurchaseInvoice d = new PurchaseInvoice();
        d.setId(id);
        d.setCompanyId(COMPANY);
        d.setNumber("PI-" + id);
        d.setVendorId(vendor);
        d.setInvoiceDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 12, 1));
        d.setStatus(status);
        return d;
    }

    private static PurchaseReturn purchaseReturn(long id, Long vendor, PurchaseReturn.EStatus status) {
        PurchaseReturn r = new PurchaseReturn();
        r.setId(id);
        r.setCompanyId(COMPANY);
        r.setNumber("PR-" + id);
        r.setInvoiceId(30L);
        r.setVendorId(vendor);
        r.setReturnDate(LocalDate.of(2026, 11, 20));
        r.setStatus(status);
        return r;
    }
}
