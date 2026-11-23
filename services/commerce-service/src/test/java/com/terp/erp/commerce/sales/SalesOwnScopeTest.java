package com.terp.erp.commerce.sales;

import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
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
import java.util.List;
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

/** Manage Own on quotations, proposals and invoices: the owner is created_by (TestLogin's sub is "kc-5"). */
class SalesOwnScopeTest {
    private static final String ME = "kc-5";
    private final SalesDocumentRepository documents = mock(SalesDocumentRepository.class);
    private final SalesDocumentService service = new SalesDocumentService(documents, mock(Numbering.class),
            mock(InventoryClient.class), mock(AccountingClient.class),
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC),
            mock(com.terp.erp.outbox.OutboxPublisher.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static SalesDocument doc(long id, EDocumentKind kind, EDocumentStatus status, String createdBy) {
        SalesDocument d = new SalesDocument();
        d.setId(id);
        d.setCompanyId(10L);
        d.setKind(kind);
        d.setStatus(status);
        d.setNumber(kind.prefix() + "-" + id);
        d.setCustomerId(5L);
        d.setCustomerName("Test Customer");
        d.setDocDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 12, 1));
        d.setTotal(BigDecimal.TEN);
        d.setCreatedBy(createdBy);
        return d;
    }

    private void searchReturnsEmpty() {
        when(documents.search(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(),
                any())).thenReturn(Page.empty());
    }

    @Test
    void ownListsCountsAndCalendarFilterOnMySub() {
        TestLogin.as(10L, "QUOTATION_VIEW_OWN", "SALES_VIEW_OWN");
        searchReturnsEmpty();
        service.search(EDocumentKind.QUOTATION, null, null, null, null, null, null, null, false, null, Pageable.unpaged());
        verify(documents).search(eq(10L), eq(EDocumentKind.QUOTATION), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), eq(ME), any());

        service.statusCounts(EDocumentKind.PROPOSAL, null, null, null, null, null, null);
        verify(documents).countByStatus(eq(10L), eq(EDocumentKind.PROPOSAL), any(), any(), any(), any(), any(), eq(ME));

        LocalDate from = LocalDate.of(2026, 11, 1);
        service.calendar(EDocumentKind.INVOICE, null, from, from.plusDays(30));
        verify(documents).dueBetween(10L, EDocumentKind.INVOICE, from, from.plusDays(30), ME);
    }

    @Test
    void allModeIsUnfiltered() {
        TestLogin.as(10L, "SALES_VIEW", "SALES_VIEW_OWN");
        searchReturnsEmpty();
        service.search(EDocumentKind.INVOICE, null, null, null, null, null, null, null, false, null, Pageable.unpaged());
        verify(documents).search(eq(10L), eq(EDocumentKind.INVOICE), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), isNull(), any());
        when(documents.findByIdAndDeletedFalse(7L))
                .thenReturn(Optional.of(doc(7L, EDocumentKind.INVOICE, EDocumentStatus.DRAFT, "kc-other")));
        assertThat(service.get(EDocumentKind.INVOICE, 7L).document().id()).isEqualTo("7");
    }

    @Test
    void anotherUsersDocumentAnswers404ForEachKind() {
        TestLogin.as(10L, "QUOTATION_VIEW_OWN", "QUOTATION_MANAGE_OWN", "SALES_VIEW_OWN", "SALES_MANAGE_OWN");
        long id = 1;
        for (EDocumentKind kind : EDocumentKind.values()) {
            long other = id++, mine = id++;
            when(documents.findByIdAndDeletedFalse(other))
                    .thenReturn(Optional.of(doc(other, kind, EDocumentStatus.DRAFT, "kc-other")));
            when(documents.findByIdAndDeletedFalse(mine))
                    .thenReturn(Optional.of(doc(mine, kind, EDocumentStatus.DRAFT, ME)));
            assertThatThrownBy(() -> service.get(kind, other))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
            assertThatThrownBy(() -> service.delete(kind, other))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
            assertThat(service.get(kind, mine).document().id()).isEqualTo(String.valueOf(mine));
        }
    }

    @Test
    void viewAllWithManageOwnStillManagesOnlyMine() {
        TestLogin.as(10L, "QUOTATION_VIEW", "QUOTATION_MANAGE_OWN");
        when(documents.findByIdAndDeletedFalse(3L))
                .thenReturn(Optional.of(doc(3L, EDocumentKind.QUOTATION, EDocumentStatus.SENT, "kc-other")));
        assertThat(service.get(EDocumentKind.QUOTATION, 3L).document().id()).isEqualTo("3");
        assertThatThrownBy(() -> service.decide(EDocumentKind.QUOTATION, 3L, true))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
    }

    @Test
    void neitherCodeIs403() {
        TestLogin.as(10L, "QUOTATION_VIEW");
        when(documents.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(doc(1L, EDocumentKind.PROPOSAL, EDocumentStatus.DRAFT, ME)));
        assertThatThrownBy(() -> service.get(EDocumentKind.PROPOSAL, 1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommonError.COMMON_004);
    }

    @Test
    void postWithOnlyOwnCodesReachesOnlyMyInvoicesWhileAPurePosterReachesAll() {
        when(documents.findByIdAndDeletedFalse(9L))
                .thenReturn(Optional.of(doc(9L, EDocumentKind.INVOICE, EDocumentStatus.POSTED, "kc-other")));
        TestLogin.as(10L, "SALES_POST", "SALES_VIEW_OWN");
        assertThatThrownBy(() -> service.retryJournal(9L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);
        assertThatThrownBy(() -> service.requirePostedInvoice(9L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_001);

        TestLogin.as(10L, "SALES_POST");
        // reaches the invoice: fails on its journal state, not on ownership
        assertThatThrownBy(() -> service.retryJournal(9L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_025);
        assertThat(service.requirePostedInvoice(9L).getId()).isEqualTo(9L);
    }

    @Test
    void decideOnALoadedDocumentOnlyFromSent() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        SalesDocument sent = doc(4L, EDocumentKind.PROPOSAL, EDocumentStatus.SENT, "kc-other");
        assertThat(service.decide(sent, false).document().status()).isEqualTo(EDocumentStatus.REJECTED);
        assertThatThrownBy(() -> service.decide(sent, true))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_003);
        assertThat(List.of(sent.getStatus())).containsExactly(EDocumentStatus.REJECTED);
    }
}
