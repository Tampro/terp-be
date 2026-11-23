package com.terp.erp.commerce.salesreturn;

import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.sales.SalesDocumentService;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesReturnOwnScopeTest {
    private final SalesReturnRepository returns = mock(SalesReturnRepository.class);
    private final SalesReturnService service = new SalesReturnService(returns, mock(SalesDocumentService.class),
            mock(Numbering.class), mock(InventoryClient.class), mock(AccountingClient.class),
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC),
            mock(com.terp.erp.outbox.OutboxPublisher.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static SalesReturn ret(long id, String createdBy) {
        SalesReturn r = new SalesReturn();
        r.setId(id);
        r.setCompanyId(10L);
        r.setNumber("SR-" + id);
        r.setInvoiceId(40L);
        r.setCustomerId(5L);
        r.setCustomerName("Test Customer");
        r.setReturnDate(LocalDate.of(2026, 11, 20));
        r.setCreatedBy(createdBy);
        return r;
    }

    @Test
    void ownListFiltersOnMySubAndAllDoesNot() {
        when(returns.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        TestLogin.as(10L, "SALES_VIEW_OWN");
        service.search(null, null, null, null, null, null, null, null, Pageable.unpaged());
        verify(returns).search(eq(10L), any(), any(), any(), any(), any(), any(), any(), eq("kc-5"), any());

        TestLogin.as(10L, "SALES_VIEW");
        service.search(null, null, null, null, null, null, null, null, Pageable.unpaged());
        verify(returns).search(eq(10L), any(), any(), any(), any(), any(), any(), any(), isNull(), any());
    }

    @Test
    void anotherUsersReturnAnswers404() {
        when(returns.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(ret(70L, "kc-other")));
        when(returns.findByIdAndDeletedFalse(71L)).thenReturn(Optional.of(ret(71L, "kc-5")));
        TestLogin.as(10L, "SALES_VIEW_OWN", "SALES_MANAGE_OWN");
        assertThatThrownBy(() -> service.get(70L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_012);
        assertThatThrownBy(() -> service.delete(70L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_012);
        assertThat(service.get(71L).ret().id()).isEqualTo("71");

        TestLogin.as(10L, "SALES_VIEW");
        assertThat(service.get(70L).ret().id()).isEqualTo("70");
    }
}
