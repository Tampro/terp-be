package com.terp.erp.commerce.purchase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.purchase.PurchaseInvoiceService.InvoiceForm;
import com.terp.erp.commerce.purchase.PurchaseInvoiceService.LineForm;
import com.terp.erp.commerce.purchase.PurchaseReturnService.ReturnForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private final PurchaseInvoiceRepository invoiceRepo = mock(PurchaseInvoiceRepository.class);
    private final PurchaseReturnRepository returnRepo = mock(PurchaseReturnRepository.class);
    private final Numbering numbering = mock(Numbering.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final PurchaseInvoiceService invoices = new PurchaseInvoiceService(invoiceRepo, numbering, inventory,
            accounting, clock);
    private final PurchaseReturnService returns = new PurchaseReturnService(returnRepo, invoices, numbering, inventory,
            clock);

    @BeforeEach
    void setUp() {
        when(invoiceRepo.save(any(PurchaseInvoice.class))).thenAnswer(inv -> {
            PurchaseInvoice d = inv.getArgument(0);
            d.setId(40L);
            long lineId = 500;
            for (PurchaseInvoice.Line l : d.getLines()) {
                l.setId(lineId++);
            }
            return d;
        });
        when(returnRepo.save(any(PurchaseReturn.class))).thenAnswer(inv -> {
            PurchaseReturn r = inv.getArgument(0);
            r.setId(70L);
            return r;
        });
        when(numbering.next(anyLong(), anyString(), any())).thenAnswer(inv -> inv.getArgument(1) + "-2026-11-001");
        when(accounting.vendor(3L)).thenReturn(new AccountingClient.Vendor("3", "VEN-0001", "Test Supplier", null));
        when(inventory.item(1L)).thenReturn(new InventoryClient.Item("1", "PRODUCT", "Test Widget", "W-1", "VAT 10",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("60")));
        when(inventory.item(2L)).thenReturn(new InventoryClient.Item("2", "SERVICE", "Setup", "S-1", null, null,
                new BigDecimal("50"), new BigDecimal("20")));
        when(returnRepo.returned(any(), any())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invoiceUsesThePurchasePriceGetsAPiNumberAndPostingAddsStock() {
        TestLogin.as(10L, "PURCHASE_MANAGE", "PURCHASE_POST");
        var d = invoices.create(form(2L, List.of(new LineForm(1L, new BigDecimal("5"), null, null),
                new LineForm(2L, null, null, null))));
        // 5 × 60 = 300 + 10% = 330; service 20 → total 350
        assertThat(d.invoice().number()).isEqualTo("PI-2026-11-001");
        assertThat(d.invoice().total()).isEqualByComparingTo("350");
        assertThat(d.invoice().vendorName()).isEqualTo("Test Supplier");
        PurchaseInvoice saved = stored();

        var posted = invoices.post(40L);
        assertThat(posted.invoice().status()).isEqualTo(PurchaseInvoice.EStatus.POSTED);
        assertThat(posted.invoice().balance()).isEqualByComparingTo("350");
        verify(inventory).move(eq("PURCHASE_INVOICE"), eq(40L), argThat(lines -> lines.size() == 1
                && lines.get(0).quantity().compareTo(new BigDecimal("5")) == 0 && lines.get(0).warehouseId() == 2L));
        assertThatThrownBy(() -> invoices.post(40L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_002);
        assertThat(saved.getStatus()).isEqualTo(PurchaseInvoice.EStatus.POSTED);
    }

    @Test
    void stockItemsNeedAWarehouseToPostAndDueDateCannotBeEarly() {
        TestLogin.as(10L, "PURCHASE_MANAGE", "PURCHASE_POST");
        invoices.create(form(null, List.of(new LineForm(1L, null, null, null))));
        stored();
        assertThatThrownBy(() -> invoices.post(40L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_005);
        verify(inventory, never()).move(anyString(), anyLong(), anyList());
        assertThatThrownBy(() -> invoices.create(new InvoiceForm(null, 3L, 2L, TODAY, TODAY.minusDays(1), null, null,
                List.of(new LineForm(1L, null, null, null)))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_004);
    }

    @Test
    void anotherCompanysInvoiceIsNotFound() {
        TestLogin.as(10L, "PURCHASE_MANAGE");
        invoices.create(form(2L, List.of(new LineForm(1L, null, null, null))));
        stored();
        TestLogin.as(20L, "PURCHASE_VIEW", "PURCHASE_POST");
        assertThatThrownBy(() -> invoices.get(40L)).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
            assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
        assertThatThrownBy(() -> invoices.post(40L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
    }

    @Test
    void returnIsLimitedToTheReturnableQuantityAndCompletingTakesStockOut() {
        TestLogin.as(10L, "PURCHASE_MANAGE", "PURCHASE_POST");
        invoices.create(form(2L, List.of(new LineForm(1L, new BigDecimal("5"), null, null))));
        stored();
        ReturnForm draftOnly = returnForm(new BigDecimal("1"));
        assertThatThrownBy(() -> returns.create(draftOnly))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_006);
        invoices.post(40L);
        when(returnRepo.returned(any(), any())).thenReturn(Map.of(500L, new BigDecimal("3")));
        assertThatThrownBy(() -> returns.create(returnForm(new BigDecimal("3"))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_007);

        var r = returns.create(returnForm(new BigDecimal("2")));
        assertThat(r.ret().number()).isEqualTo("PR-2026-11-001");
        assertThat(r.ret().warehouseId()).isEqualTo("2");
        assertThat(r.ret().total()).isEqualByComparingTo("132");
        PurchaseReturn saved = storedReturn();
        assertThatThrownBy(() -> returns.complete(70L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_010);
        returns.approve(70L);
        returns.complete(70L);
        assertThat(saved.getStatus()).isEqualTo(PurchaseReturn.EStatus.COMPLETED);
        verify(inventory).move(eq("PURCHASE_RETURN"), eq(70L), argThat(lines -> lines.size() == 1
                && lines.get(0).quantity().compareTo(new BigDecimal("-2")) == 0));
    }

    @Test
    void nothingLeftToReturnIsSaid() {
        TestLogin.as(10L, "PURCHASE_MANAGE", "PURCHASE_POST");
        invoices.create(form(2L, List.of(new LineForm(1L, new BigDecimal("5"), null, null))));
        stored();
        invoices.post(40L);
        when(returnRepo.returned(any(), any())).thenReturn(Map.of(500L, new BigDecimal("5")));
        assertThatThrownBy(() -> returns.returnable(40L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_012);
    }

    private PurchaseInvoice stored() {
        var captor = org.mockito.ArgumentCaptor.forClass(PurchaseInvoice.class);
        verify(invoiceRepo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        PurchaseInvoice d = captor.getValue();
        when(invoiceRepo.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(d));
        return d;
    }

    private PurchaseReturn storedReturn() {
        var captor = org.mockito.ArgumentCaptor.forClass(PurchaseReturn.class);
        verify(returnRepo).save(captor.capture());
        PurchaseReturn r = captor.getValue();
        when(returnRepo.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void calendarListsTheCallersInvoicesFallingDueAndLimitsThePeriod() {
        TestLogin.as(10L, "PURCHASE_VIEW");
        PurchaseInvoice d = new PurchaseInvoice();
        d.setId(40L);
        d.setCompanyId(10L);
        d.setNumber("PI-2026-11-001");
        d.setVendorName("Test Supplier");
        d.setInvoiceDate(TODAY);
        d.setDueDate(TODAY.plusDays(30));
        LocalDate from = TODAY;
        LocalDate to = from.plusDays(61);
        when(invoiceRepo.dueBetween(10L, from, to)).thenReturn(List.of(d));
        assertThat(invoices.calendar(99L, from, to)).singleElement()
                .satisfies(e -> assertThat(e.number()).isEqualTo("PI-2026-11-001"));
        assertThatThrownBy(() -> invoices.calendar(null, from, to.plusDays(1)))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_013));
        TestLogin.superAdmin();
        assertThatThrownBy(() -> invoices.calendar(null, from, to))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_003));
    }

    private static InvoiceForm form(Long warehouseId, List<LineForm> lines) {
        return new InvoiceForm(null, 3L, warehouseId, TODAY, TODAY.plusDays(30), "Net 30", null, lines);
    }

    private static ReturnForm returnForm(BigDecimal qty) {
        return new ReturnForm(40L, null, TODAY, "Wrong model", null,
                List.of(new PurchaseReturnService.LineForm(500L, qty, null)));
    }
}
