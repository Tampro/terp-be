package com.terp.erp.inventory.stock;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Stock per item and warehouse. Every change locks the affected warehouse_stock rows (SELECT … FOR UPDATE), refuses
 * the whole change when one row would go negative (INV_017) and writes one immutable movement per line.
 */
@Service
@RequiredArgsConstructor
public class StockService {
    private final WarehouseStockRepository stocks;
    private final StockMovementRepository movements;
    private final ItemRepository items;
    private final WarehouseRepository warehouseRows;
    private final WarehouseService warehouses;

    public record StockDto(String itemId, String warehouseId, String warehouseName, BigDecimal quantity) {
    }

    public record AddStockForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long itemId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warehouseId,
            @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) {
    }

    public record MoveLine(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long itemId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warehouseId,
            @Schema(description = "Signed: + into the warehouse, - out of it; never 0", example = "-2",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull BigDecimal quantity) {
        @AssertTrue(message = "must not be zero")
        @Schema(hidden = true)
        public boolean isNonZero() {
            return quantity == null || quantity.signum() != 0;
        }
    }

    public record MovesForm(
            @Schema(example = "SALES_INVOICE", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Pattern(regexp = "^[A-Z_]{1,30}$") String sourceType,
            @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long sourceId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Size(min = 1, max = 200)
            List<@Valid @NotNull MoveLine> lines) {
    }

    /** applied = false: the source had already been applied, nothing changed. */
    public record MovesResult(boolean applied) {
    }

    /** A resolved line of a stock change: item and warehouse already checked to belong to the company. */
    public record Change(Item item, Warehouse warehouse, BigDecimal quantity) {
    }

    @Transactional(readOnly = true)
    public List<StockDto> find(Long requestedCompanyId, Long itemId, Long warehouseId) {
        Long companyId = Texts.company(requestedCompanyId);
        List<WarehouseStock> rows = stocks.find(companyId, itemId, warehouseId);
        Map<Long, String> names = warehouseRows.findAllById(rows.stream().map(WarehouseStock::getWarehouseId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        return rows.stream().map(s -> new StockDto(s.getItemId().toString(), s.getWarehouseId().toString(),
                names.get(s.getWarehouseId()), s.getQuantity())).toList();
    }

    @Transactional
    public StockDto add(Long requestedCompanyId, AddStockForm form) {
        Long companyId = Texts.company(requestedCompanyId);
        Change c = new Change(stockedItemOf(companyId, form.itemId()), warehouses.activeOf(companyId,
                form.warehouseId()), form.quantity());
        apply(companyId, List.of(c), StockMovement.MANUAL, null);
        WarehouseStock s = stocks.lock(companyId, c.item().getId(), c.warehouse().getId()).orElseThrow();
        return new StockDto(Texts.id(s.getItemId()), Texts.id(s.getWarehouseId()), c.warehouse().getName(),
                s.getQuantity());
    }

    /**
     * Applies a document's stock lines once. A second call with the same (company, sourceType, sourceId) changes
     * nothing and answers applied = false, so commerce-service can retry safely.
     */
    @Transactional
    public MovesResult moves(Long requestedCompanyId, MovesForm form) {
        Long companyId = Texts.company(requestedCompanyId);
        // Locking read first: two concurrent calls for the same source serialize on the index range; the loser of a
        // rare deadlock gets an error, and its retry answers applied = false.
        if (!movements.findTop1ByCompanyIdAndSourceTypeAndSourceId(companyId, form.sourceType(), form.sourceId())
                .isEmpty()) {
            return new MovesResult(false);
        }
        Map<Long, Item> itemCache = new HashMap<>();
        Map<Long, Warehouse> warehouseCache = new HashMap<>();
        List<Change> changes = new ArrayList<>();
        for (MoveLine l : form.lines()) {
            changes.add(new Change(itemCache.computeIfAbsent(l.itemId(), id -> stockedItemOf(companyId, id)),
                    warehouseCache.computeIfAbsent(l.warehouseId(), id -> warehouses.activeOf(companyId, id)),
                    l.quantity()));
        }
        apply(companyId, changes, form.sourceType(), form.sourceId());
        return new MovesResult(true);
    }

    /**
     * All-or-nothing inside the caller's transaction: every new quantity is computed and checked before any row is
     * written. Rows are locked in (item, warehouse) order so two changes over the same rows cannot deadlock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Long companyId, List<Change> changes, String sourceType, Long sourceId) {
        record Key(Long itemId, Long warehouseId) {
        }
        Comparator<Key> order = Comparator.comparing(Key::itemId).thenComparing(Key::warehouseId);
        Map<Key, BigDecimal> deltas = new TreeMap<>(order);
        Map<Long, Item> byId = new LinkedHashMap<>();
        for (Change c : changes) {
            if (!c.item().getType().stocked()) {
                throw ApiException.badRequest(EInventoryError.INV_016);
            }
            byId.put(c.item().getId(), c.item());
            deltas.merge(new Key(c.item().getId(), c.warehouse().getId()), c.quantity(), BigDecimal::add);
        }
        Map<Key, WarehouseStock> rows = new TreeMap<>(order);
        Map<Key, BigDecimal> next = new HashMap<>();
        for (Map.Entry<Key, BigDecimal> e : deltas.entrySet()) {
            Key k = e.getKey();
            WarehouseStock s = stocks.lock(companyId, k.itemId(), k.warehouseId()).orElseGet(() -> {
                WarehouseStock n = new WarehouseStock();
                n.setCompanyId(companyId);
                n.setItemId(k.itemId());
                n.setWarehouseId(k.warehouseId());
                return n;
            });
            BigDecimal qty = s.getQuantity().add(e.getValue());
            if (qty.signum() < 0) {
                throw ApiException.badRequest(EInventoryError.INV_017, byId.get(k.itemId()).getSku(),
                        s.getQuantity().stripTrailingZeros().toPlainString());
            }
            rows.put(k, s);
            next.put(k, qty);
        }
        rows.forEach((k, s) -> {
            s.setQuantity(next.get(k));
            if (s.getId() == null) {
                stocks.save(s);
            }
        });
        movements.saveAll(changes.stream().map(c -> new StockMovement(companyId, c.item().getId(),
                c.warehouse().getId(), c.quantity(), sourceType, sourceId)).toList());
    }

    /** An item named in a stock change: of the company (INV_020) and stocked (INV_016). */
    private Item stockedItemOf(Long companyId, Long id) {
        Item i = items.findByIdAndDeletedFalse(id).filter(x -> Objects.equals(x.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EInventoryError.INV_020));
        if (!i.getType().stocked()) {
            throw ApiException.badRequest(EInventoryError.INV_016);
        }
        return i;
    }

    /** Sum over warehouses of each item; items without stock rows are absent. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> totals(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return stocks.sumByItems(itemIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1], BigDecimal::add));
    }
}
