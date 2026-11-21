package com.terp.erp.commerce.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.sales.SalesDtos.DocumentForm;
import com.terp.erp.commerce.sales.SalesDtos.LineForm;
import com.terp.erp.exception.ApiException;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesDocumentServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private final SalesDocumentRepository documents = mock(SalesDocumentRepository.class);
    private final Numbering numbering = mock(Numbering.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final SalesDocumentService service = new SalesDocumentService(documents, numbering, inventory, accounting,
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC));
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        when(documents.save(any(SalesDocument.class))).thenAnswer(inv -> {
            SalesDocument d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(nextId++);
            }
            long lineId = 500;
            for (SalesDocumentLine l : d.getLines()) {
                if (l.getId() == null) {
                    l.setId(lineId++);
                }
            }
            return d;
        });
        when(numbering.next(anyLong(), anyString(), any())).thenAnswer(inv -> inv.getArgument(1) + "-2026-11-001");
        when(accounting.customer(5L)).thenReturn(new AccountingClient.Customer("5", "CUST-0001", "Test Customer", null));
        when(inventory.item(1L)).thenReturn(new InventoryClient.Item("1", "PRODUCT", "Test Widget", "W-1", "VAT 10",
                new BigDecimal("10"), new BigDecimal("100")));
        when(inventory.item(2L)).thenReturn(new InventoryClient.Item("2", "SERVICE", "Setup", "S-1", null, null,
                new BigDecimal("50")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invoiceTotalsFollowTheLineArithmeticAndGetANumber() {
        TestLogin.as(10L, "SALES_MANAGE");

        var d = service.create(EDocumentKind.INVOICE, form(null, 2L, List.of(
                new LineForm(1L, new BigDecimal("2"), null, new BigDecimal("10")),
                new LineForm(2L, null, new BigDecimal("80"), null))));

        // widget: base 200, discount 20, tax 18 → 198; setup: base 80, no tax → 80
        assertThat(d.document().number()).isEqualTo("SI-2026-11-001");
        assertThat(d.document().invoiceType()).isEqualTo(EInvoiceType.PRODUCT);
        assertThat(d.document().subtotal()).isEqualByComparingTo("280");
        assertThat(d.document().discount()).isEqualByComparingTo("20");
        assertThat(d.document().tax()).isEqualByComparingTo("18");
        assertThat(d.document().total()).isEqualByComparingTo("278");
        assertThat(d.document().balance()).isEqualByComparingTo("278");
        assertThat(d.document().customerName()).isEqualTo("Test Customer");
        assertThat(d.lines().get(0).lineTotal()).isEqualByComparingTo("198");
        assertThat(d.lines().get(0).taxName()).isEqualTo("VAT 10");
        assertThat(d.lines().get(1).unitPrice()).isEqualByComparingTo("80");
    }

    @Test
    void documentOfAnotherCompanyIsNotFound() {
        TestLogin.as(10L, "SALES_VIEW");
        SalesDocument other = doc(EDocumentKind.INVOICE, 20L, EDocumentStatus.DRAFT);
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.get(EDocumentKind.INVOICE, 7L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        // a proposal id asked as an invoice is not found either
        when(documents.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(doc(EDocumentKind.PROPOSAL, 10L,
                EDocumentStatus.DRAFT)));
        assertThatThrownBy(() -> service.get(EDocumentKind.INVOICE, 8L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
    }

    @Test
    void postingAProductInvoiceTakesItsStockedLinesOutOfTheWarehouse() {
        TestLogin.as(10L, "SALES_POST");
        SalesDocument d = doc(EDocumentKind.INVOICE, 10L, EDocumentStatus.DRAFT);
        d.setInvoiceType(EInvoiceType.PRODUCT);
        d.setWarehouseId(3L);
        d.getLines().add(line(1L, "PRODUCT", "2"));
        d.getLines().add(line(2L, "SERVICE", "1"));
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(d));

        var posted = service.post(7L);

        verify(inventory).move(eq("SALES_INVOICE"), eq(7L),
                eq(List.of(new InventoryClient.MoveLine(1L, 3L, new BigDecimal("2").negate()))));
        assertThat(posted.document().status()).isEqualTo(EDocumentStatus.POSTED);
        assertThatThrownBy(() -> service.update(EDocumentKind.INVOICE, 7L, form(null, 3L, List.of(
                new LineForm(1L, BigDecimal.ONE, null, null)))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_002);
    }

    @Test
    void productInvoiceWithoutWarehouseCannotBePostedAndServiceInvoiceMovesNoStock() {
        TestLogin.as(10L, "SALES_POST");
        SalesDocument noWarehouse = doc(EDocumentKind.INVOICE, 10L, EDocumentStatus.DRAFT);
        noWarehouse.setInvoiceType(EInvoiceType.PRODUCT);
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(noWarehouse));
        assertThatThrownBy(() -> service.post(7L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_009);

        SalesDocument services = doc(EDocumentKind.INVOICE, 10L, EDocumentStatus.DRAFT);
        services.setInvoiceType(EInvoiceType.SERVICE);
        when(documents.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(services));
        service.post(9L);
        verify(inventory, never()).move(anyString(), anyLong(), anyList());
    }

    @Test
    void serviceWiseInvoiceRefusesStockItemsAndQuantitiesOtherThanOne() {
        TestLogin.as(10L, "SALES_MANAGE");
        DocumentForm stock = new DocumentForm(null, 5L, null, EInvoiceType.SERVICE, TODAY, TODAY, null, null,
                List.of(new LineForm(1L, null, null, null)));
        assertThatThrownBy(() -> service.create(EDocumentKind.INVOICE, stock))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_008);
        DocumentForm twice = new DocumentForm(null, 5L, null, EInvoiceType.SERVICE, TODAY, TODAY, null, null,
                List.of(new LineForm(2L, new BigDecimal("2"), null, null)));
        assertThatThrownBy(() -> service.create(EDocumentKind.INVOICE, twice))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_019);
        DocumentForm late = new DocumentForm(null, 5L, null, null, TODAY, TODAY.minusDays(1), null, null,
                List.of(new LineForm(1L, null, null, null)));
        assertThatThrownBy(() -> service.create(EDocumentKind.QUOTATION, late))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_007);
    }

    @Test
    void anAcceptedProposalConvertsOnceAndKeepsItsStatus() {
        TestLogin.as(10L, "SALES_MANAGE");
        SalesDocument p = doc(EDocumentKind.PROPOSAL, 10L, EDocumentStatus.ACCEPTED);
        p.setWarehouseId(3L);
        p.getLines().add(line(1L, "PRODUCT", "2"));
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(p));

        var inv = service.convert(EDocumentKind.PROPOSAL, 7L);

        assertThat(inv.document().kind()).isEqualTo(EDocumentKind.INVOICE);
        assertThat(inv.document().status()).isEqualTo(EDocumentStatus.DRAFT);
        assertThat(inv.document().invoiceType()).isEqualTo(EInvoiceType.PRODUCT);
        assertThat(inv.document().convertedFromId()).isEqualTo("7");
        assertThat(inv.lines()).hasSize(1);
        assertThat(p.getStatus()).isEqualTo(EDocumentStatus.ACCEPTED);
        assertThat(p.getConvertedInvoiceId()).isEqualTo(Long.valueOf(inv.document().id()));
        assertThatThrownBy(() -> service.convert(EDocumentKind.PROPOSAL, 7L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_005);
    }

    @Test
    void onlySentDocumentsAreDecidedAndDraftsAreNotConverted() {
        TestLogin.as(10L, "QUOTATION_MANAGE");
        SalesDocument q = doc(EDocumentKind.QUOTATION, 10L, EDocumentStatus.DRAFT);
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(q));
        assertThatThrownBy(() -> service.decide(EDocumentKind.QUOTATION, 7L, true))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_003);
        assertThatThrownBy(() -> service.convert(EDocumentKind.QUOTATION, 7L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_004);
        assertThatThrownBy(() -> service.newVersion(7L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_017);
        service.send(EDocumentKind.QUOTATION, 7L);
        assertThat(service.decide(EDocumentKind.QUOTATION, 7L, false).document().status())
                .isEqualTo(EDocumentStatus.REJECTED);
    }

    @Test
    void aNewVersionPointsToTheFirstVersionWithTheNextNumber() {
        TestLogin.as(10L, "QUOTATION_MANAGE");
        SalesDocument q = doc(EDocumentKind.QUOTATION, 10L, EDocumentStatus.REJECTED);
        q.getLines().add(line(1L, "PRODUCT", "1"));
        when(documents.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(q));
        when(documents.maxVersion(7L)).thenReturn(2);

        var v = service.newVersion(7L);

        assertThat(v.document().versionOfId()).isEqualTo("7");
        assertThat(v.document().versionNo()).isEqualTo(3);
        assertThat(v.document().status()).isEqualTo(EDocumentStatus.DRAFT);
        assertThat(v.document().docDate()).isEqualTo(TODAY);
    }

    @Test
    void overdueAndExpiredAreReadFromTheDueDate() {
        SalesDocument inv = doc(EDocumentKind.INVOICE, 10L, EDocumentStatus.POSTED);
        inv.setDueDate(TODAY.minusDays(1));
        assertThat(SalesDtos.DocumentRow.of(inv, TODAY).overdue()).isTrue();
        inv.setStatus(EDocumentStatus.PAID);
        assertThat(SalesDtos.DocumentRow.of(inv, TODAY).overdue()).isFalse();
        SalesDocument p = doc(EDocumentKind.PROPOSAL, 10L, EDocumentStatus.SENT);
        p.setDueDate(TODAY.minusDays(1));
        assertThat(SalesDtos.DocumentRow.of(p, TODAY).expired()).isTrue();
    }

    private static DocumentForm form(EInvoiceType type, Long warehouseId, List<LineForm> lines) {
        return new DocumentForm(null, 5L, warehouseId, type, TODAY, TODAY.plusDays(30), "Net 30", null, lines);
    }

    private static SalesDocument doc(EDocumentKind kind, Long companyId, EDocumentStatus status) {
        SalesDocument d = new SalesDocument();
        d.setId(7L);
        d.setCompanyId(companyId);
        d.setKind(kind);
        d.setNumber(kind.prefix() + "-2026-11-007");
        d.setCustomerId(5L);
        d.setCustomerName("Test Customer");
        d.setDocDate(TODAY.minusDays(5));
        d.setDueDate(TODAY.plusDays(25));
        d.setStatus(status);
        return d;
    }

    private static SalesDocumentLine line(Long itemId, String type, String qty) {
        SalesDocumentLine l = new SalesDocumentLine();
        l.setId(itemId * 10);
        l.setPosition(1);
        l.setItemId(itemId);
        l.setItemType(type);
        l.setItemName("Item " + itemId);
        l.setSku("SKU-" + itemId);
        l.setQuantity(new BigDecimal(qty));
        l.setUnitPrice(new BigDecimal("100"));
        return l;
    }
}
