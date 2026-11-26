package com.terp.erp.inventory.storeitem;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Price and on-sale status of items per store (warehouse). Back Office only; the storefront reads it later. */
@Service
@RequiredArgsConstructor
public class StoreItemService {
    /** TODO(assumption): bulk size 1,000 (brief 13 §11 default) — confirm with PO. */
    public static final int BULK_MAX = 1000;

    private final StoreItemRepository rows;
    private final ItemRepository items;
    private final WarehouseRepository warehouseRows;
    private final WarehouseService warehouses;

    public record StoreItemForm(
            @Schema(example = "245", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warehouseId,
            @Schema(example = "1201", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long itemId,
            @Schema(example = "5990", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal salePrice,
            @Schema(description = "Default ON_SALE", example = "ON_SALE") EStoreItemStatus status,
            @Schema(description = "Max quantity per order; omit = no limit", example = "10") @Min(1)
            Integer purchaseLimitQty) {
    }

    public record StoreItemUpdateForm(
            @Schema(example = "6490", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal salePrice,
            @Schema(example = "ON_SALE", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStoreItemStatus status,
            @Schema(description = "Omit = no limit", example = "10") @Min(1) Integer purchaseLimitQty) {
    }

    public record StatusForm(
            @Schema(example = "SUSPENDED", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            EStoreItemStatus status) {
    }

    public record BulkLine(
            @Schema(example = "245", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warehouseId,
            @Schema(example = "134291579", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String sku,
            @Schema(example = "5990", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal salePrice,
            @Schema(description = "Default ON_SALE", example = "ON_SALE") EStoreItemStatus status,
            @Schema(example = "10") @Min(1) Integer purchaseLimitQty) {
    }

    public record BulkForm(
            @Schema(description = "At most 1000 lines; all or nothing", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty List<@NotNull @Valid BulkLine> lines) {
    }

    public record StoreItemDto(String id, String warehouseId, String warehouseName, String itemId, String itemName,
                               String sku, String categoryId, BigDecimal salePrice, EStoreItemStatus status,
                               Integer purchaseLimitQty, BigDecimal stockQuantity, Instant updatedDate) {
    }

    public record BulkResult(int created, int updated) {
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<StoreItemDto> search(Long requestedCompanyId, Long warehouseId, Long categoryId,
                                     EStoreItemStatus status, String keyword, Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<StoreItem> page = rows.search(companyId, warehouseId, categoryId, status, Texts.kw(keyword), pageable);
        Map<Long, StoreItemDto> dtos = toDtos(companyId, page.getContent()).stream()
                .collect(Collectors.toMap(d -> Long.valueOf(d.id()), Function.identity()));
        return page.map(s -> dtos.get(s.getId()));
    }

    @Transactional(readOnly = true)
    public StoreItemDto get(Long id) {
        StoreItem s = require(id);
        return toDtos(s.getCompanyId(), List.of(s)).get(0);
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public StoreItemDto create(Long requestedCompanyId, StoreItemForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        Item item = stockedItemOf(companyId, f.itemId());
        Warehouse w = warehouses.activeOf(companyId, f.warehouseId());
        if (rows.existsByCompanyIdAndWarehouseIdAndItemIdAndDeletedFalse(companyId, w.getId(), item.getId())) {
            throw ApiException.badRequest(EInventoryError.INV_026);
        }
        StoreItem s = new StoreItem();
        s.setCompanyId(companyId);
        s.setWarehouseId(w.getId());
        s.setItemId(item.getId());
        apply(s, f.salePrice(), f.status() == null ? EStoreItemStatus.ON_SALE : f.status(), f.purchaseLimitQty());
        save(List.of(s));
        return toDtos(companyId, List.of(s)).get(0);
    }

    /** Full replacement of salePrice, status and purchaseLimitQty; warehouse and item never change. */
    @Transactional
    public StoreItemDto update(Long id, StoreItemUpdateForm f) {
        StoreItem s = require(id);
        apply(s, f.salePrice(), f.status(), f.purchaseLimitQty());
        return toDtos(s.getCompanyId(), List.of(s)).get(0);
    }

    @Transactional
    public StoreItemDto status(Long id, StatusForm f) {
        StoreItem s = require(id);
        s.setStatus(f.status());
        return toDtos(s.getCompanyId(), List.of(s)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        StoreItem s = require(id);
        s.setDeleted(true);
        s.setDeletedKey(s.getId());
    }

    /**
     * Upsert by (warehouseId, sku), all or nothing: every line is checked before any row is written; the first bad
     * line answers INV_028 with its 1-based number and the reason's code.
     */
    @Transactional
    public BulkResult bulk(Long requestedCompanyId, BulkForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        if (f.lines().size() > BULK_MAX) {
            throw ApiException.badRequest(EInventoryError.INV_029, BULK_MAX);
        }
        Map<String, Item> bySku = items.findByCompanyIdAndSkuInAndDeletedFalse(companyId,
                        f.lines().stream().map(l -> l.sku().trim()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Item::getSku, Function.identity(), (a, b) -> a));
        Set<Long> warehouseIds = f.lines().stream().map(BulkLine::warehouseId).collect(Collectors.toSet());
        Map<Long, Warehouse> byWarehouse = warehouseRows.findAllById(warehouseIds).stream()
                .filter(w -> !w.isDeleted() && w.isActive() && Objects.equals(w.getCompanyId(), companyId))
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Set<String> seen = new HashSet<>();
        List<Item> lineItems = new ArrayList<>();
        for (int i = 0; i < f.lines().size(); i++) {
            BulkLine l = f.lines().get(i);
            Item item = bySku.get(l.sku().trim());
            String reason = item == null ? EInventoryError.INV_020.name()
                    : !item.getType().stocked() ? EInventoryError.INV_016.name()
                    : !byWarehouse.containsKey(l.warehouseId()) ? EInventoryError.INV_015.name()
                    : !seen.add(l.warehouseId() + "/" + item.getId()) ? EInventoryError.INV_026.name() : null;
            if (reason != null) {
                throw ApiException.badRequest(EInventoryError.INV_028, i + 1, reason);
            }
            lineItems.add(item);
        }
        Map<String, StoreItem> existing = new HashMap<>();
        for (StoreItem s : rows.findLive(companyId, warehouseIds,
                lineItems.stream().map(Item::getId).collect(Collectors.toSet()))) {
            existing.put(s.getWarehouseId() + "/" + s.getItemId(), s);
        }
        List<StoreItem> created = new ArrayList<>();
        int updated = 0;
        for (int i = 0; i < f.lines().size(); i++) {
            BulkLine l = f.lines().get(i);
            Item item = lineItems.get(i);
            StoreItem s = existing.get(l.warehouseId() + "/" + item.getId());
            if (s == null) {
                s = new StoreItem();
                s.setCompanyId(companyId);
                s.setWarehouseId(l.warehouseId());
                s.setItemId(item.getId());
                created.add(s);
            } else {
                updated++;
            }
            apply(s, l.salePrice(), l.status() == null ? EStoreItemStatus.ON_SALE : l.status(), l.purchaseLimitQty());
        }
        save(created);
        return new BulkResult(created.size(), updated);
    }

    // ------------------------------------------------------------------ helpers

    /** By-id access: another company's row answers 404 INV_027. */
    private StoreItem require(Long id) {
        StoreItem s = rows.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EInventoryError.INV_027));
        TenantGuard.assertSameCompany(s.getCompanyId(), EInventoryError.INV_027);
        return s;
    }

    /** Same checks as a stock change: of the company (INV_020) and stocked (INV_016). */
    private Item stockedItemOf(Long companyId, Long id) {
        Item i = items.findByIdAndDeletedFalse(id).filter(x -> Objects.equals(x.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EInventoryError.INV_020));
        if (!i.getType().stocked()) {
            throw ApiException.badRequest(EInventoryError.INV_016);
        }
        return i;
    }

    private static void apply(StoreItem s, BigDecimal price, EStoreItemStatus status, Integer limit) {
        s.setSalePrice(price);
        s.setStatus(status);
        s.setPurchaseLimitQty(limit);
    }

    /** A concurrent writer of the same live (warehouse, item) hits uk_store_item: answered as a duplicate, not 500. */
    private void save(List<StoreItem> news) {
        try {
            rows.saveAllAndFlush(news);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest(EInventoryError.INV_026);
        }
    }

    private List<StoreItemDto> toDtos(Long companyId, List<StoreItem> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> itemIds = list.stream().map(StoreItem::getItemId).collect(Collectors.toSet());
        Map<Long, Item> byItem = items.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, String> names = warehouseRows.findAllById(list.stream().map(StoreItem::getWarehouseId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<String, BigDecimal> stock = new HashMap<>();
        for (Object[] r : rows.stockOf(companyId, itemIds)) {
            stock.put(r[0] + "/" + r[1], (BigDecimal) r[2]);
        }
        return list.stream().map(s -> {
            Item i = byItem.get(s.getItemId());
            return new StoreItemDto(Texts.id(s.getId()), Texts.id(s.getWarehouseId()), names.get(s.getWarehouseId()),
                    Texts.id(s.getItemId()), i == null ? null : i.getName(), i == null ? null : i.getSku(),
                    i == null ? null : Texts.id(i.getCategoryId()), s.getSalePrice(), s.getStatus(),
                    s.getPurchaseLimitQty(), stock.getOrDefault(s.getItemId() + "/" + s.getWarehouseId(),
                    BigDecimal.ZERO), s.getUpdatedDate());
        }).toList();
    }
}
