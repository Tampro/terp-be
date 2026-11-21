package com.terp.erp.commerce.salesreturn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.sales.EDocumentKind;
import com.terp.erp.commerce.sales.EDocumentStatus;
import com.terp.erp.commerce.sales.EInvoiceType;
import com.terp.erp.commerce.sales.SalesDocument;
import com.terp.erp.commerce.sales.SalesDocumentLine;
import com.terp.erp.commerce.sales.SalesDocumentService;
import com.terp.erp.commerce.salesreturn.SalesReturnService.LineForm;
import com.terp.erp.commerce.salesreturn.SalesReturnService.ReturnForm;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesReturnServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private final SalesReturnRepository returns = mock(SalesReturnRepository.class);
    private final SalesDocumentService documents = mock(SalesDocumentService.class);
    private final Numbering numbering = mock(Numbering.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final SalesReturnService service = new SalesReturnService(returns, documents, numbering, inventory,
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC));
    private final SalesDocument invoice = new SalesDocument();

    @BeforeEach
    void setUp() {
        invoice.setId(40L);
        invoice.setCompanyId(10L);
        invoice.setKind(EDocumentKind.INVOICE);
        invoice.setInvoiceType(EInvoiceType.PRODUCT);
        invoice.setStatus(EDocumentStatus.POSTED);
        invoice.setWarehouseId(3L);
        invoice.setCustomerId(5L);
        invoice.setCustomerName("Test Customer");
        SalesDocumentLine l = new SalesDocumentLine();
        l.setId(31L);
        l.setItemId(1L);
        l.setItemType("PRODUCT");
        l.setItemName("Test Widget");
        l.setSku("W-1");
        l.setQuantity(new BigDecimal("5"));
        l.setUnitPrice(new BigDecimal("100"));
        l.setDiscountPct(new BigDecimal("10"));
        l.setTaxRate(new BigDecimal("10"));
        invoice.getLines().add(l);
        when(documents.requirePostedInvoice(40L)).thenReturn(invoice);
        when(numbering.next(anyLong(), anyString(), any())).thenReturn("SR-2026-11-001");
        when(returns.save(any(SalesReturn.class))).thenAnswer(inv -> {
            SalesReturn r = inv.getArgument(0);
            r.setId(70L);
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnIsPricedLikeTheInvoiceLineAndUsesTheInvoiceWarehouse() {
        TestLogin.as(10L, "SALES_MANAGE");
        when(returns.returned(any(), isNull())).thenReturn(java.util.Map.of(31L, new BigDecimal("1")));

        var r = service.create(new ReturnForm(40L, null, TODAY, "Excess quantity", null,
                List.of(new LineForm(31L, new BigDecimal("2"), null))));

        // 2 × 100 = 200, −10% = 180, +10% tax = 18 → 198
        assertThat(r.ret().number()).isEqualTo("SR-2026-11-001");
        assertThat(r.ret().warehouseId()).isEqualTo("3");
        assertThat(r.ret().total()).isEqualByComparingTo("198");
        assertThat(r.tax()).isEqualByComparingTo("18");
        assertThat(r.ret().status()).isEqualTo(SalesReturn.EStatus.DRAFT);
    }

    @Test
    void cannotReturnMoreThanIsStillReturnable() {
        TestLogin.as(10L, "SALES_MANAGE");
        when(returns.returned(any(), isNull())).thenReturn(java.util.Map.of(31L, new BigDecimal("4")));

        assertThatThrownBy(() -> service.create(new ReturnForm(40L, null, TODAY, null, null,
                List.of(new LineForm(31L, new BigDecimal("2"), null)))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_011);
        // two lines of the same invoice line add up
        when(returns.returned(any(), isNull())).thenReturn(java.util.Map.of());
        assertThatThrownBy(() -> service.create(new ReturnForm(40L, null, TODAY, null, null,
                List.of(new LineForm(31L, new BigDecimal("3"), null), new LineForm(31L, new BigDecimal("3"), null)))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_011);
        assertThatThrownBy(() -> service.create(new ReturnForm(40L, null, TODAY, null, null,
                List.of(new LineForm(99L, BigDecimal.ONE, null)))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_018);
    }

    @Test
    void completingAnApprovedReturnPutsTheStockBack() {
        TestLogin.as(10L, "SALES_POST");
        SalesReturn r = new SalesReturn();
        r.setId(70L);
        r.setCompanyId(10L);
        r.setWarehouseId(3L);
        r.setStatus(SalesReturn.EStatus.DRAFT);
        SalesReturn.Line l = new SalesReturn.Line();
        l.setInvoiceLineId(31L);
        l.setItemName("Test Widget");
        l.setUnitPrice(new BigDecimal("100"));
        l.setItemId(1L);
        l.setItemType("PRODUCT");
        l.setQuantity(new BigDecimal("2"));
        r.getLines().add(l);
        r.setInvoiceId(40L);
        r.setCustomerId(5L);
        when(returns.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.complete(70L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_014);
        service.approve(70L);
        var done = service.complete(70L);

        verify(inventory).move(eq("SALES_RETURN"), eq(70L),
                eq(List.of(new InventoryClient.MoveLine(1L, 3L, new BigDecimal("2")))));
        assertThat(done.ret().status()).isEqualTo(SalesReturn.EStatus.COMPLETED);
        assertThatThrownBy(() -> service.delete(70L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_013);
    }

    @Test
    void returnOfAnotherCompanyIsNotFound() {
        TestLogin.as(10L, "SALES_VIEW");
        SalesReturn r = new SalesReturn();
        r.setId(70L);
        r.setCompanyId(20L);
        when(returns.findByIdAndDeletedFalse(70L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.get(70L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_012);
    }
}
