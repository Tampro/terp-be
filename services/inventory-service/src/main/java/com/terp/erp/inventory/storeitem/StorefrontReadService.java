package com.terp.erp.inventory.storeitem;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.catalog.CategoryRepository;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemImage;
import com.terp.erp.inventory.item.ItemImageRepository;
import com.terp.erp.inventory.item.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a customer may see of a store (warehouse): live items with an ON_SALE store item, its price, sold-out flag.
 * Called by store-service with a service-account token, so the company comes as a parameter (store-service resolved
 * it from the shop slug) and every query filters it — never a path id alone.
 */
@Service
@RequiredArgsConstructor
public class StorefrontReadService {
    private final StoreItemRepository rows;
    private final ItemRepository items;
    private final ItemImageRepository images;
    private final CategoryRepository categories;

    public record CategoryDto(String id, String name, String color) {
    }

    /** No purchase price, no stock quantity: only sold-out. */
    public record ShelfItemDto(String itemId, String name, String sku, String categoryId, BigDecimal salePrice,
                               Integer purchaseLimitQty, boolean soldOut, String mainImageId) {
    }

    public record ShelfItemDetailDto(ShelfItemDto item, String shortDescription, String description,
                                     List<String> imageIds) {
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories(long companyId) {
        return categories.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(c -> new CategoryDto(Texts.id(c.getId()), c.getName(), c.getColor())).toList();
    }

    @Transactional(readOnly = true)
    public Page<ShelfItemDto> shelf(long companyId, long warehouseId, Long categoryId, String keyword, Pageable pageable) {
        Page<StoreItem> page = rows.search(companyId, warehouseId, categoryId, EStoreItemStatus.ON_SALE,
                Texts.kw(keyword), pageable);
        Set<Long> itemIds = page.getContent().stream().map(StoreItem::getItemId).collect(Collectors.toSet());
        Map<Long, Item> byId = itemIds.isEmpty() ? Map.of()
                : items.findAllById(itemIds).stream().collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, BigDecimal> stock = stock(companyId, warehouseId, itemIds);
        return page.map(s -> shelfItem(s, byId.get(s.getItemId()), stock));
    }

    /** At most this many items per batch (a cart holds at most 100 lines). */
    public static final int MAX_BATCH = 100;

    /**
     * Live shelf data of several items at a store, in the order asked; items not ON_SALE there (or not of the company,
     * or deleted) are left out, so the caller marks them unavailable. More than 100 ids → 400 INV_029.
     */
    @Transactional(readOnly = true)
    public List<ShelfItemDto> shelfItems(long companyId, long warehouseId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_BATCH) {
            throw ApiException.badRequest(EInventoryError.INV_029, MAX_BATCH);
        }
        Set<Long> wanted = new java.util.LinkedHashSet<>(ids);
        Map<Long, Item> byId = items.findAllById(wanted).stream()
                .filter(i -> !i.isDeleted() && Objects.equals(i.getCompanyId(), companyId))
                .collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, StoreItem> onSale = rows.findLive(companyId, List.of(warehouseId), byId.keySet()).stream()
                .filter(r -> r.getStatus() == EStoreItemStatus.ON_SALE)
                .collect(Collectors.toMap(StoreItem::getItemId, r -> r, (a, b) -> a));
        Map<Long, BigDecimal> stock = stock(companyId, warehouseId, onSale.keySet());
        return wanted.stream().filter(onSale::containsKey)
                .map(id -> shelfItem(onSale.get(id), byId.get(id), stock)).toList();
    }

    /** 404 INV_027 when the item is not on sale at this store (missing, suspended, deleted, other company). */
    @Transactional(readOnly = true)
    public ShelfItemDetailDto detail(long companyId, long warehouseId, long itemId) {
        Item item = items.findByIdAndDeletedFalse(itemId)
                .filter(i -> Objects.equals(i.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.notFound(EInventoryError.INV_027));
        StoreItem s = rows.findLive(companyId, List.of(warehouseId), List.of(itemId)).stream()
                .filter(r -> r.getStatus() == EStoreItemStatus.ON_SALE).findFirst()
                .orElseThrow(() -> ApiException.notFound(EInventoryError.INV_027));
        List<String> imageIds = images.findByItemIdOrderBySortOrderAsc(itemId).stream()
                .map(ItemImage::getMediaFileId).map(Texts::id).toList();
        return new ShelfItemDetailDto(shelfItem(s, item, stock(companyId, warehouseId, Set.of(itemId))),
                item.getShortDescription(), item.getDescription(), imageIds);
    }

    private Map<Long, BigDecimal> stock(long companyId, long warehouseId, Set<Long> itemIds) {
        Map<Long, BigDecimal> m = new HashMap<>();
        if (itemIds.isEmpty()) {
            return m;
        }
        for (Object[] r : rows.stockOf(companyId, itemIds)) {
            if (Objects.equals(r[1], warehouseId)) {
                m.put((Long) r[0], (BigDecimal) r[2]);
            }
        }
        return m;
    }

    private static ShelfItemDto shelfItem(StoreItem s, Item i, Map<Long, BigDecimal> stock) {
        BigDecimal q = stock.getOrDefault(s.getItemId(), BigDecimal.ZERO);
        return new ShelfItemDto(Texts.id(s.getItemId()), i == null ? null : i.getName(), i == null ? null : i.getSku(),
                i == null ? null : Texts.id(i.getCategoryId()), s.getSalePrice(), s.getPurchaseLimitQty(),
                q.signum() <= 0, i == null ? null : Texts.id(i.getMainImageId()));
    }
}
