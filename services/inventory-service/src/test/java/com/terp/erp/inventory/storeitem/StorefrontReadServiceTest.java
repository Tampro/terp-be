package com.terp.erp.inventory.storeitem;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.catalog.CategoryRepository;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemImageRepository;
import com.terp.erp.inventory.item.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorefrontReadServiceTest {
    private final StoreItemRepository rows = mock(StoreItemRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final StorefrontReadService service = new StorefrontReadService(rows, items, mock(ItemImageRepository.class),
            mock(CategoryRepository.class));

    @Test
    void shelfListsOnSaleOnlyWithSoldOutFromThisWarehouse() {
        StoreItem egg = row(1L, 100L), milk = row(2L, 101L);
        when(rows.search(eq(10L), eq(5L), any(), eq(EStoreItemStatus.ON_SALE), any(), any()))
                .thenReturn(new PageImpl<>(List.of(egg, milk)));
        when(items.findAllById(any())).thenReturn(List.of(item(100L, 10L), item(101L, 10L)));
        // egg: 3 here and 0 elsewhere; milk: 7 in another warehouse only → sold out here
        when(rows.stockOf(eq(10L), anyCollection())).thenReturn(List.of(
                new Object[]{100L, 5L, new BigDecimal("3")}, new Object[]{100L, 6L, BigDecimal.ZERO},
                new Object[]{101L, 6L, new BigDecimal("7")}));
        List<StorefrontReadService.ShelfItemDto> shelf = service.shelf(10L, 5L, null, null, PageRequest.of(0, 20)).getContent();
        assertThat(shelf).extracting(StorefrontReadService.ShelfItemDto::soldOut).containsExactly(false, true);
        assertThat(shelf.get(0).salePrice()).isEqualByComparingTo("5990");
    }

    @Test
    void detailOfAnotherCompanysItemOrNotOnSaleIs404() {
        when(items.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(item(100L, 20L)));
        assertThatThrownBy(() -> service.detail(10L, 5L, 100L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_027));
        when(items.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(item(101L, 10L)));
        StoreItem suspended = row(2L, 101L);
        suspended.setStatus(EStoreItemStatus.SUSPENDED);
        when(rows.findLive(eq(10L), anyCollection(), anyCollection())).thenReturn(List.of(suspended));
        assertThatThrownBy(() -> service.detail(10L, 5L, 101L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EInventoryError.INV_027));
    }

    private static StoreItem row(Long id, Long itemId) {
        StoreItem s = new StoreItem();
        s.setId(id);
        s.setCompanyId(10L);
        s.setWarehouseId(5L);
        s.setItemId(itemId);
        s.setSalePrice(new BigDecimal("5990"));
        s.setStatus(EStoreItemStatus.ON_SALE);
        return s;
    }

    private static Item item(Long id, Long companyId) {
        Item i = new Item();
        i.setId(id);
        i.setCompanyId(companyId);
        i.setName("Item " + id);
        i.setSku("SKU-" + id);
        i.setCategoryId(1L);
        return i;
    }
}
