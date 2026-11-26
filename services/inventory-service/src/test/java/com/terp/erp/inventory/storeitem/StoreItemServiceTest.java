package com.terp.erp.inventory.storeitem;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.TestLogin;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.Item.EItemType;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.stock.WarehouseStockRepository;
import com.terp.erp.inventory.storeitem.StoreItemService.BulkForm;
import com.terp.erp.inventory.storeitem.StoreItemService.BulkLine;
import com.terp.erp.inventory.storeitem.StoreItemService.StoreItemForm;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import com.terp.erp.outbox.OutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreItemServiceTest {
    private final StoreItemRepository rows = mock(StoreItemRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final WarehouseRepository warehouseRows = mock(WarehouseRepository.class);
    private final StoreItemService service = new StoreItemService(rows, items, warehouseRows,
            new WarehouseService(warehouseRows, mock(WarehouseStockRepository.class), mock(OutboxPublisher.class)));
    private final Item egg = item(1L, "EGG-20", EItemType.PRODUCT);
    private final Item delivery = item(2L, "SVC-01", EItemType.SERVICE);
    private final Warehouse store = warehouse(7L, 10L);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "INVENTORY_MANAGE");
        when(items.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(egg));
        when(items.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(delivery));
        when(items.findAllById(any())).thenReturn(List.of(egg));
        when(warehouseRows.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(store));
        when(warehouseRows.findAllById(any())).thenReturn(List.of(store));
        when(rows.saveAllAndFlush(anyList())).thenAnswer(a -> a.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void duplicatePairIsRefused() {
        when(rows.existsByCompanyIdAndWarehouseIdAndItemIdAndDeletedFalse(10L, 7L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(null, form(1L)))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_026));
        verify(rows, never()).saveAllAndFlush(anyList());
    }

    @Test
    void serviceItemIsRefused() {
        assertThatThrownBy(() -> service.create(null, form(2L)))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_016));
    }

    @Test
    void anotherCompanysRowAnswersNotFound() {
        StoreItem other = row(99L, 20L);
        when(rows.findByIdAndDeletedFalse(99L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.get(99L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_027));
        assertThatThrownBy(() -> service.delete(99L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_027));
        assertThat(other.isDeleted()).isFalse();
    }

    @Test
    void deleteFreesThePairForANewRow() {
        StoreItem s = row(5L, 10L);
        when(rows.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(s));
        service.delete(5L);
        assertThat(s.isDeleted()).isTrue();
        assertThat(s.getDeletedKey()).isEqualTo(5L);
        // the live check ignores deleted rows -> creating the pair again is allowed
        when(rows.existsByCompanyIdAndWarehouseIdAndItemIdAndDeletedFalse(10L, 7L, 1L)).thenReturn(false);
        assertThat(service.create(null, form(1L)).status()).isEqualTo(EStoreItemStatus.ON_SALE);
    }

    @Test
    void badBulkLineWritesNothing() {
        when(items.findByCompanyIdAndSkuInAndDeletedFalse(eq(10L), anyCollection())).thenReturn(List.of(egg));
        BulkForm f = new BulkForm(List.of(line("EGG-20"), line("NOPE-1")));
        assertThatThrownBy(() -> service.bulk(null, f)).satisfies(e -> {
            ApiException a = (ApiException) e;
            assertThat(a.getErrorCode()).isEqualTo(EInventoryError.INV_028);
            assertThat(a.getArgs()).containsExactly(2, "INV_020");
        });
        verify(rows, never()).saveAllAndFlush(anyList());
    }

    @Test
    void bulkOverTheLimitIsRefused() {
        List<BulkLine> lines = IntStream.range(0, StoreItemService.BULK_MAX + 1).mapToObj(i -> line("EGG-20")).toList();
        assertThatThrownBy(() -> service.bulk(null, new BulkForm(lines)))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_029));
    }

    @Test
    void bulkTwiceUpdatesTheSameRow() {
        when(items.findByCompanyIdAndSkuInAndDeletedFalse(eq(10L), anyCollection())).thenReturn(List.of(egg));
        BulkForm f = new BulkForm(List.of(line("EGG-20")));
        List<StoreItem> stored = new ArrayList<>();
        when(rows.saveAllAndFlush(anyList())).thenAnswer(a -> {
            List<StoreItem> news = a.getArgument(0);
            stored.addAll(news);
            return news;
        });
        when(rows.findLive(eq(10L), anyCollection(), anyCollection())).thenAnswer(a -> List.copyOf(stored));
        assertThat(service.bulk(null, f)).isEqualTo(new StoreItemService.BulkResult(1, 0));
        assertThat(service.bulk(null, f)).isEqualTo(new StoreItemService.BulkResult(0, 1));
        assertThat(stored).hasSize(1);
    }

    private static StoreItemForm form(Long itemId) {
        return new StoreItemForm(7L, itemId, new BigDecimal("5990"), null, 10);
    }

    private static BulkLine line(String sku) {
        return new BulkLine(7L, sku, new BigDecimal("5990"), null, null);
    }

    private static Item item(Long id, String sku, EItemType type) {
        Item i = new Item();
        i.setId(id);
        i.setCompanyId(10L);
        i.setSku(sku);
        i.setName(sku);
        i.setType(type);
        return i;
    }

    private static Warehouse warehouse(Long id, Long companyId) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCompanyId(companyId);
        w.setName("Store " + id);
        return w;
    }

    private static StoreItem row(Long id, Long companyId) {
        StoreItem s = new StoreItem();
        s.setId(id);
        s.setCompanyId(companyId);
        s.setWarehouseId(7L);
        s.setItemId(1L);
        s.setSalePrice(BigDecimal.TEN);
        return s;
    }
}
