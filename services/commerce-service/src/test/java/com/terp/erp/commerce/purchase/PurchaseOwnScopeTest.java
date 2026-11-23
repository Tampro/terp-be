package com.terp.erp.commerce.purchase;

import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOwnScopeTest {
    private final PurchaseInvoiceRepository invoiceRepo = mock(PurchaseInvoiceRepository.class);
    private final PurchaseReturnRepository returnRepo = mock(PurchaseReturnRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final PurchaseInvoiceService invoices = new PurchaseInvoiceService(invoiceRepo, mock(Numbering.class),
            mock(InventoryClient.class), mock(AccountingClient.class), clock, outbox);
    private final PurchaseReturnService returns = new PurchaseReturnService(returnRepo, invoices, mock(Numbering.class),
            mock(InventoryClient.class), mock(AccountingClient.class), clock, outbox);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static PurchaseInvoice invoice(long id, PurchaseInvoice.EStatus status, String createdBy) {
        PurchaseInvoice d = new PurchaseInvoice();
        d.setId(id);
        d.setCompanyId(10L);
        d.setNumber("PI-" + id);
        d.setVendorId(3L);
        d.setVendorName("Test Vendor");
        d.setInvoiceDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 12, 1));
        d.setStatus(status);
        d.setTotal(BigDecimal.TEN);
        d.setCreatedBy(createdBy);
        return d;
    }

    static PurchaseReturn ret(long id, PurchaseReturn.EStatus status, String createdBy) {
        PurchaseReturn r = new PurchaseReturn();
        r.setId(id);
        r.setCompanyId(10L);
        r.setNumber("PR-" + id);
        r.setInvoiceId(30L);
        r.setVendorId(3L);
        r.setVendorName("Test Vendor");
        r.setReturnDate(LocalDate.of(2026, 11, 20));
        r.setStatus(status);
        r.setCreatedBy(createdBy);
        return r;
    }

    @Test
    void ownInvoiceListCountsAndCalendarFilterOnMySub() {
        TestLogin.as(10L, "PURCHASE_VIEW_OWN");
        when(invoiceRepo.search(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(),
                any())).thenReturn(Page.empty());
        invoices.search(null, null, null, null, null, null, null, false, null, Pageable.unpaged());
        verify(invoiceRepo).search(eq(10L), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(),
                eq("kc-5"), any());
        invoices.statusCounts(null, null, null, null, null, null);
        verify(invoiceRepo).countByStatus(eq(10L), any(), any(), any(), any(), any(), eq("kc-5"));
        LocalDate from = LocalDate.of(2026, 11, 1);
        invoices.calendar(null, from, from.plusDays(10));
        verify(invoiceRepo).dueBetween(10L, from, from.plusDays(10), "kc-5");
    }

    @Test
    void allModeInvoiceListIsUnfiltered() {
        TestLogin.as(10L, "PURCHASE_VIEW");
        when(invoiceRepo.search(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(),
                any())).thenReturn(Page.empty());
        invoices.search(null, null, null, null, null, null, null, false, null, Pageable.unpaged());
        verify(invoiceRepo).search(eq(10L), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(),
                isNull(), any());
    }

    @Test
    void anotherUsersPurchaseInvoiceAnswers404() {
        when(invoiceRepo.findByIdAndDeletedFalse(30L))
                .thenReturn(Optional.of(invoice(30L, PurchaseInvoice.EStatus.DRAFT, "kc-other")));
        when(invoiceRepo.findByIdAndDeletedFalse(31L))
                .thenReturn(Optional.of(invoice(31L, PurchaseInvoice.EStatus.DRAFT, "kc-5")));
        TestLogin.as(10L, "PURCHASE_VIEW_OWN", "PURCHASE_MANAGE_OWN", "PURCHASE_POST");
        assertThatThrownBy(() -> invoices.get(30L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
        assertThatThrownBy(() -> invoices.delete(30L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
        assertThatThrownBy(() -> invoices.post(30L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
        assertThat(invoices.get(31L).invoice().id()).isEqualTo("31");

        TestLogin.as(10L, "PURCHASE_VIEW");
        assertThat(invoices.get(30L).invoice().id()).isEqualTo("30");
    }

    @Test
    void ownReturnListAndAnotherUsersReturnAnswers404() {
        when(returnRepo.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        TestLogin.as(10L, "PURCHASE_VIEW_OWN");
        returns.search(null, null, null, null, null, null, null, null, Pageable.unpaged());
        verify(returnRepo).search(eq(10L), any(), any(), any(), any(), any(), any(), any(), eq("kc-5"), any());

        when(returnRepo.findByIdAndDeletedFalse(80L))
                .thenReturn(Optional.of(ret(80L, PurchaseReturn.EStatus.DRAFT, "kc-other")));
        assertThatThrownBy(() -> returns.get(80L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_008);
        TestLogin.as(10L, "PURCHASE_VIEW");
        assertThat(returns.get(80L).ret().id()).isEqualTo("80");
    }
}
