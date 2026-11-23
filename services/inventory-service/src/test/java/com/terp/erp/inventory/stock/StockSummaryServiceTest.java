package com.terp.erp.inventory.stock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockSummaryServiceTest {
    private final ItemRepository items = mock(ItemRepository.class);
    private final WarehouseStockRepository stocks = mock(WarehouseStockRepository.class);
    private final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    private final StockSummaryService service = new StockSummaryService(items, stocks, warehouses);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Warehouse wh(long id, String name) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setName(name);
        w.setCompanyId(10L);
        return w;
    }

    @Test
    void countsOfTheCallersCompanyOnly() {
        TestLogin.as(10L, "INVENTORY_VIEW");
        when(items.countStocked(10L)).thenReturn(12L);
        when(items.countOutOfStock(10L)).thenReturn(3L);
        when(stocks.outOfStockByWarehouse(10L)).thenReturn(List.<Object[]>of(new Object[]{2L, 4L}));
        when(warehouses.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(wh(1, "Main"), wh(2, "Shop")));
        when(items.countStocked(20L)).thenReturn(99L);

        var s = service.summary(20L);

        assertThat(s.products()).isEqualTo(12);
        assertThat(s.outOfStock()).isEqualTo(3);
        assertThat(s.byWarehouse()).extracting(StockSummaryService.WarehouseOutOfStock::warehouseName)
                .containsExactly("Shop", "Main");
        assertThat(s.byWarehouse().get(0).outOfStock()).isEqualTo(4);
        assertThat(s.byWarehouse().get(1).outOfStock()).isZero();
        verify(stocks).outOfStockByWarehouse(10L);
        verify(items, org.mockito.Mockito.never()).countStocked(20L);
        verify(warehouses, org.mockito.Mockito.never()).findByCompanyIdAndDeletedFalse(20L);
    }

    @Test
    void handlerPermission() throws Exception {
        PreAuthorize p = StockController.class.getMethod("summary", Long.class).getAnnotation(PreAuthorize.class);
        assertThat(p.value()).isEqualTo("@PermissionChecker.hasAny('INVENTORY_VIEW','POS_REPORT','POS_SELL')");
    }
}
