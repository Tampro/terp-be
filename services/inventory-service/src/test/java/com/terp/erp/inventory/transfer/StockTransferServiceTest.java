package com.terp.erp.inventory.transfer;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.stock.StockMovement;
import com.terp.erp.inventory.stock.StockMovementRepository;
import com.terp.erp.inventory.stock.StockService;
import com.terp.erp.inventory.stock.WarehouseStock;
import com.terp.erp.inventory.stock.WarehouseStockRepository;
import com.terp.erp.inventory.transfer.StockTransferService.TransferForm;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockTransferServiceTest {
    private final WarehouseStockRepository stocks = mock(WarehouseStockRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final WarehouseRepository warehouseRows = mock(WarehouseRepository.class);
    private final StockTransferRepository transfers = mock(StockTransferRepository.class);
    private final WarehouseService warehouses = new WarehouseService(warehouseRows, stocks, org.mockito.Mockito.mock(com.terp.erp.outbox.OutboxPublisher.class));
    private final StockTransferService service = new StockTransferService(transfers, warehouses, items,
            new StockService(stocks, movements, items, warehouseRows, warehouses));
    private final Item mouse = new Item();
    private final Warehouse other = warehouse(9L, 11L);
    private final WarehouseStock inFrom = stock(7L, "5");
    private final WarehouseStock inTo = stock(8L, "0");

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        mouse.setId(1L);
        mouse.setCompanyId(10L);
        mouse.setType(Item.EItemType.PRODUCT);
        mouse.setSku("MOUSE-01");
        when(warehouseRows.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(warehouse(7L, 10L)));
        when(warehouseRows.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(warehouse(8L, 10L)));
        when(warehouseRows.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(other));
        when(items.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(mouse));
        when(items.findById(1L)).thenReturn(Optional.of(mouse));
        when(stocks.lock(10L, 1L, 7L)).thenReturn(Optional.of(inFrom));
        when(stocks.lock(10L, 1L, 8L)).thenReturn(Optional.of(inTo));
        when(transfers.save(any())).thenAnswer(a -> {
            StockTransfer t = a.getArgument(0);
            t.setId(50L);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anotherCompanysWarehouseIsNotFound() {
        assertThatThrownBy(() -> service.create(null, form(7L, 9L, "1")))
                .satisfies(e -> {
                    ApiException a = (ApiException) e;
                    assertThat(a.getErrorCode()).isEqualTo(EInventoryError.INV_007);
                    assertThat(a.getStatus().value()).isEqualTo(404);
                });
        verify(transfers, never()).save(any());
    }

    @Test
    void sameWarehouseIsRefused() {
        assertThatThrownBy(() -> service.create(null, form(7L, 7L, "1")))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_022));
    }

    @Test
    void serviceItemIsRefused() {
        mouse.setType(Item.EItemType.SERVICE);
        assertThatThrownBy(() -> service.create(null, form(7L, 8L, "1")))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_016));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createMovesOutOfSourceIntoDestination() {
        assertThat(service.create(null, form(7L, 8L, "2")).id()).isEqualTo("50");
        assertThat(inFrom.getQuantity()).isEqualByComparingTo("3");
        assertThat(inTo.getQuantity()).isEqualByComparingTo("2");
        ArgumentCaptor<List<StockMovement>> saved = ArgumentCaptor.forClass(List.class);
        verify(movements).saveAll(saved.capture());
        assertThat(saved.getValue()).allSatisfy(m -> {
            assertThat(m.getSourceType()).isEqualTo(StockMovement.TRANSFER);
            assertThat(m.getSourceId()).isEqualTo(50L);
        });
    }

    @Test
    void insufficientStockInSourceIsRefused() {
        assertThatThrownBy(() -> service.create(null, form(7L, 8L, "6")))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_017));
        assertThat(inFrom.getQuantity()).isEqualByComparingTo("5");
        assertThat(inTo.getQuantity()).isEqualByComparingTo("0");
        verify(movements, never()).saveAll(anyList());
    }

    @Test
    void deleteReversesTheTransfer() {
        inFrom.setQuantity(new BigDecimal("3"));
        inTo.setQuantity(new BigDecimal("2"));
        StockTransfer t = transfer();
        service.delete(50L);
        assertThat(inFrom.getQuantity()).isEqualByComparingTo("5");
        assertThat(inTo.getQuantity()).isEqualByComparingTo("0");
        assertThat(t.isDeleted()).isTrue();
    }

    @Test
    void deleteIsRefusedWhenDestinationNoLongerHoldsIt() {
        inTo.setQuantity(new BigDecimal("1"));
        StockTransfer t = transfer();
        assertThatThrownBy(() -> service.delete(50L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_017));
        assertThat(t.isDeleted()).isFalse();
        assertThat(inTo.getQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void anotherCompanysTransferIsNotFound() {
        StockTransfer t = transfer();
        t.setCompanyId(11L);
        assertThatThrownBy(() -> service.delete(50L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_023));
    }

    private StockTransfer transfer() {
        StockTransfer t = new StockTransfer();
        t.setId(50L);
        t.setCompanyId(10L);
        t.setFromWarehouseId(7L);
        t.setToWarehouseId(8L);
        t.setItemId(1L);
        t.setQuantity(new BigDecimal("2"));
        t.setTransferDate(LocalDate.of(2026, 11, 21));
        when(transfers.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(t));
        return t;
    }

    private static TransferForm form(Long from, Long to, String qty) {
        return new TransferForm(from, to, 1L, new BigDecimal(qty), LocalDate.of(2026, 11, 21));
    }

    private static Warehouse warehouse(Long id, Long companyId) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCompanyId(companyId);
        w.setName("W" + id);
        return w;
    }

    private static WarehouseStock stock(Long warehouseId, String qty) {
        WarehouseStock s = new WarehouseStock();
        s.setId(warehouseId + 100);
        s.setCompanyId(10L);
        s.setItemId(1L);
        s.setWarehouseId(warehouseId);
        s.setQuantity(new BigDecimal(qty));
        return s;
    }
}
