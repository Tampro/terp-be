package com.terp.erp.inventory.stock;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.stock.StockService.MoveLine;
import com.terp.erp.inventory.stock.StockService.MovesForm;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {
    private final WarehouseStockRepository stocks = mock(WarehouseStockRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final WarehouseRepository warehouseRows = mock(WarehouseRepository.class);
    private final StockService service = new StockService(stocks, movements, items, warehouseRows,
            new WarehouseService(warehouseRows, stocks, org.mockito.Mockito.mock(com.terp.erp.outbox.OutboxPublisher.class)));
    private final Item mouse = item(1L, "MOUSE-01");
    private final Item cable = item(2L, "CABLE-01");
    private final WarehouseStock mouseStock = stock(1L, "5");
    private final WarehouseStock cableStock = stock(2L, "1");

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "SALES_POST");
        Warehouse w = new Warehouse();
        w.setId(7L);
        w.setCompanyId(10L);
        w.setName("Main");
        when(warehouseRows.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(w));
        when(items.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(mouse));
        when(items.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(cable));
        when(stocks.lock(10L, 1L, 7L)).thenReturn(Optional.of(mouseStock));
        when(stocks.lock(10L, 2L, 7L)).thenReturn(Optional.of(cableStock));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void movesAreAppliedOnceOnly() {
        MovesForm form = form(new MoveLine(1L, 7L, new BigDecimal("-2")));
        when(movements.findTop1ByCompanyIdAndSourceTypeAndSourceId(10L, "SALES_INVOICE", 42L)).thenReturn(List.of());
        assertThat(service.moves(null, form).applied()).isTrue();
        assertThat(mouseStock.getQuantity()).isEqualByComparingTo("3");
        verify(movements).saveAll(anyList());

        // second call: movements of the source exist now -> nothing changes
        when(movements.findTop1ByCompanyIdAndSourceTypeAndSourceId(10L, "SALES_INVOICE", 42L))
                .thenReturn(List.of(new StockMovement(10L, 1L, 7L, new BigDecimal("-2"), "SALES_INVOICE", 42L)));
        assertThat(service.moves(null, form).applied()).isFalse();
        assertThat(mouseStock.getQuantity()).isEqualByComparingTo("3");
        verify(movements).saveAll(anyList()); // still the single first call
    }

    @Test
    void rowIsEnsuredBeforeItIsLockedInKeyOrder() {
        // A locking read of a missing row gap-locks and deadlocks parallel inserts: ensure must precede every lock.
        when(movements.findTop1ByCompanyIdAndSourceTypeAndSourceId(10L, "SALES_INVOICE", 42L)).thenReturn(List.of());
        service.moves(null, form(new MoveLine(2L, 7L, BigDecimal.ONE), new MoveLine(1L, 7L, BigDecimal.ONE)));
        InOrder o = inOrder(stocks);
        o.verify(stocks).ensure(eq(10L), eq(1L), eq(7L), anyString());
        o.verify(stocks).lock(10L, 1L, 7L);
        o.verify(stocks).ensure(eq(10L), eq(2L), eq(7L), anyString());
        o.verify(stocks).lock(10L, 2L, 7L);
        verify(stocks, never()).save(any());
    }

    @Test
    void insufficientStockRefusesTheWholeDocument() {
        when(movements.findTop1ByCompanyIdAndSourceTypeAndSourceId(10L, "SALES_INVOICE", 42L)).thenReturn(List.of());
        // the first line fits, the second does not: neither may be applied
        MovesForm form = form(new MoveLine(1L, 7L, new BigDecimal("-2")), new MoveLine(2L, 7L, new BigDecimal("-3")));
        assertThatThrownBy(() -> service.moves(null, form)).isInstanceOf(ApiException.class).satisfies(e -> {
            ApiException a = (ApiException) e;
            assertThat(a.getErrorCode()).isEqualTo(EInventoryError.INV_017);
            assertThat(a.getArgs()).containsExactly("CABLE-01", "1");
        });
        assertThat(mouseStock.getQuantity()).isEqualByComparingTo("5");
        assertThat(cableStock.getQuantity()).isEqualByComparingTo("1");
        verify(movements, never()).saveAll(anyList());
        verify(stocks, never()).save(any());
    }

    @Test
    void anotherCompanysItemOrAServiceIsRefused() {
        when(movements.findTop1ByCompanyIdAndSourceTypeAndSourceId(any(), any(), any())).thenReturn(List.of());
        mouse.setCompanyId(11L);
        assertThatThrownBy(() -> service.moves(null, form(new MoveLine(1L, 7L, BigDecimal.ONE))))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_020));
        cable.setType(Item.EItemType.SERVICE);
        assertThatThrownBy(() -> service.moves(null, form(new MoveLine(2L, 7L, BigDecimal.ONE))))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_016));
    }

    private static MovesForm form(MoveLine... lines) {
        return new MovesForm("SALES_INVOICE", 42L, List.of(lines));
    }

    private static Item item(Long id, String sku) {
        Item i = new Item();
        i.setId(id);
        i.setCompanyId(10L);
        i.setType(Item.EItemType.PRODUCT);
        i.setSku(sku);
        return i;
    }

    private static WarehouseStock stock(Long itemId, String qty) {
        WarehouseStock s = new WarehouseStock();
        s.setId(itemId + 100);
        s.setCompanyId(10L);
        s.setItemId(itemId);
        s.setWarehouseId(7L);
        s.setQuantity(new BigDecimal(qty));
        return s;
    }
}
