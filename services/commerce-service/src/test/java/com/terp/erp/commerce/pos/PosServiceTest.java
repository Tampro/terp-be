package com.terp.erp.commerce.pos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.EPosError;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.pos.PosService.CartForm;
import com.terp.erp.commerce.pos.PosService.CartLine;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private final PosOrderRepository orders = mock(PosOrderRepository.class);
    private final Numbering numbering = mock(Numbering.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final PosService service = new PosService(orders, numbering, inventory, accounting, clock);

    @BeforeEach
    void setUp() {
        when(orders.saveAndFlush(any(PosOrder.class))).thenAnswer(inv -> {
            PosOrder o = inv.getArgument(0);
            o.setId(60L);
            return o;
        });
        when(numbering.next(anyLong(), anyString(), any())).thenAnswer(inv -> inv.getArgument(1) + "-2026-11-001");
        when(inventory.warehouse(2L)).thenReturn(new InventoryClient.Warehouse("2", "Main", true));
        when(inventory.item(1L)).thenReturn(new InventoryClient.Item("1", "PRODUCT", "Test Widget", "W-1", "VAT 10",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("60")));
        when(inventory.item(2L)).thenReturn(new InventoryClient.Item("2", "SERVICE", "Setup", "S-1", null, null,
                new BigDecimal("50"), new BigDecimal("20")));
        when(accounting.bankAccount(7L)).thenReturn(new AccountingClient.BankAccount("7", "Test Shop", "Test Bank",
                "000111", true));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkoutPricesFromItemsMovesStockAndRecordsIncome() {
        TestLogin.as(10L, "POS_SELL");
        // the same widget twice = one line of 3: 300 + 10% = 330; service 50; discount 30 → 350
        var d = service.checkout(cart(7L, new BigDecimal("30"), List.of(new CartLine(1L, new BigDecimal("2")),
                new CartLine(2L, BigDecimal.ONE), new CartLine(1L, BigDecimal.ONE))));
        assertThat(d.order().number()).isEqualTo("POS-2026-11-001");
        assertThat(d.order().receiptNumber()).isEqualTo("RCPT-2026-11-001");
        assertThat(d.order().customerName()).isEqualTo(PosService.WALK_IN);
        assertThat(d.lines()).hasSize(2);
        assertThat(d.subtotal()).isEqualByComparingTo("350");
        assertThat(d.taxTotal()).isEqualByComparingTo("30");
        assertThat(d.order().total()).isEqualByComparingTo("350");
        assertThat(d.taxes()).singleElement().satisfies(t -> assertThat(t.amount()).isEqualByComparingTo("30"));
        verify(inventory).move(eq("POS_ORDER"), eq(60L), argThat(l -> l.size() == 1
                && l.get(0).quantity().compareTo(new BigDecimal("-3")) == 0 && l.get(0).warehouseId() == 2L));
        // one journal entry: Dr bank 350 / Cr revenue 320 / Cr VAT 30; cost 3 widgets x 60 = 180 (service: none)
        verify(accounting).income(eq(7L), eq("POS_ORDER"), eq(60L), argThat(a -> a.compareTo(new BigDecimal("350")) == 0),
                argThat(t -> t.compareTo(new BigDecimal("30")) == 0), argThat(c -> c.compareTo(new BigDecimal("180")) == 0),
                eq(TODAY), anyString());
    }

    @Test
    void aBankAccountMustBeChosenAndActive() {
        TestLogin.as(10L, "POS_SELL");
        assertCode(() -> service.checkout(cart(null, null, List.of(new CartLine(1L, BigDecimal.ONE)))),
                EPosError.POS_003);
        when(accounting.bankAccount(8L)).thenReturn(new AccountingClient.BankAccount("8", "Old", "Test Bank", "9",
                false));
        assertCode(() -> service.checkout(cart(8L, null, List.of(new CartLine(1L, BigDecimal.ONE)))),
                EPosError.POS_004);
        verify(inventory, never()).move(anyString(), anyLong(), anyList());
    }

    @Test
    void discountAboveTheTotalIsRefused() {
        TestLogin.as(10L, "POS_SELL");
        assertCode(() -> service.preview(cart(null, new BigDecimal("111"), List.of(new CartLine(1L, BigDecimal.ONE)))),
                EPosError.POS_005);
        var p = service.preview(cart(null, new BigDecimal("110"), List.of(new CartLine(1L, BigDecimal.ONE))));
        assertThat(p.total()).isEqualByComparingTo("0");
    }

    @Test
    void incomeFailureAfterTheStockLeftPutsTheStockBack() {
        TestLogin.as(10L, "POS_SELL");
        ApiException down = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EPosError.POS_004);
        doThrow(down).when(accounting).income(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString());
        assertThatThrownBy(() -> service.checkout(cart(7L, null, List.of(new CartLine(1L, BigDecimal.ONE)))))
                .isSameAs(down);
        verify(inventory).move(eq("POS_ORDER_VOID"), eq(60L), argThat(l -> l.size() == 1
                && l.get(0).quantity().compareTo(BigDecimal.ONE) == 0));
    }

    @Test
    void anotherCompanysOrderIsNotFound() {
        PosOrder o = new PosOrder();
        o.setId(60L);
        o.setCompanyId(10L);
        when(orders.findById(60L)).thenReturn(Optional.of(o));
        TestLogin.as(20L, "POS_VIEW");
        assertThatThrownBy(() -> service.get(60L)).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(EPosError.POS_001);
            assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
    }

    private static CartForm cart(Long bank, BigDecimal discount, List<CartLine> lines) {
        return new CartForm(null, null, 2L, bank, discount, lines);
    }

    private static void assertCode(Runnable r, EPosError code) {
        assertThatThrownBy(r::run).extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    void closedYearFromAccountingRefusesTheSaleAndPutsTheStockBack() {
        TestLogin.as(10L, "POS_SELL");
        var closed = new com.terp.erp.commerce.client.RemoteApiException(HttpStatus.BAD_REQUEST, "ACC_070",
                "The books are closed up to 2025-12-31");
        doThrow(closed).when(accounting).income(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString());
        assertThatThrownBy(() -> service.checkout(cart(7L, null, List.of(new CartLine(1L, BigDecimal.ONE)))))
                .isSameAs(closed);
        verify(inventory).move(eq("POS_ORDER_VOID"), eq(60L), anyList());
    }
}
