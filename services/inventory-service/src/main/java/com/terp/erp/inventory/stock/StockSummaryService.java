package com.terp.erp.inventory.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stock figures of the POS dashboard: stocked products, out of stock, out of stock per warehouse. */
@Service
@RequiredArgsConstructor
public class StockSummaryService {
    private final ItemRepository items;
    private final WarehouseStockRepository stocks;
    private final WarehouseRepository warehouses;

    public record WarehouseOutOfStock(String warehouseId, String warehouseName, long outOfStock) {
    }

    public record StockSummary(long products, long outOfStock, List<WarehouseOutOfStock> byWarehouse) {
    }

    @Transactional(readOnly = true)
    public StockSummary summary(Long requestedCompanyId) {
        Long companyId = Texts.company(requestedCompanyId);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : stocks.outOfStockByWarehouse(companyId)) {
            counts.put((Long) r[0], ((Number) r[1]).longValue());
        }
        // TODO(assumption): an item never stocked in a warehouse (no stock row) is not out of stock there.
        List<WarehouseOutOfStock> byWarehouse = warehouses.findByCompanyIdAndDeletedFalse(companyId).stream()
                .map((Warehouse w) -> new WarehouseOutOfStock(w.getId().toString(), w.getName(),
                        counts.getOrDefault(w.getId(), 0L)))
                .sorted(Comparator.comparingLong(WarehouseOutOfStock::outOfStock).reversed()
                        .thenComparing(WarehouseOutOfStock::warehouseName))
                .toList();
        return new StockSummary(items.countStocked(companyId), items.countOutOfStock(companyId), byWarehouse);
    }
}
