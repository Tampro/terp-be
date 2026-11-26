package com.terp.erp.inventory.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    /**
     * Creates the row at quantity 0 unless it exists. Run before {@link #lock}: a locking read of a missing row takes a
     * gap lock on uk_warehouse_stock, and two transactions that each gap-locked the same gap then deadlock on their
     * inserts (seen when items with opening stock were created in parallel into one warehouse). Inserts of different
     * keys do not block each other, and on an existing row this only takes its record lock.
     */
    @Modifying
    @Query(nativeQuery = true, value = "insert into warehouse_stock (company_id, item_id, warehouse_id, quantity, "
            + "created_by, created_date, updated_by, updated_date) values (:companyId, :itemId, :warehouseId, 0, "
            + ":by, utc_timestamp(6), :by, utc_timestamp(6)) on duplicate key update quantity = quantity")
    int ensure(Long companyId, Long itemId, Long warehouseId, String by);

    /** SELECT … FOR UPDATE: the row stays locked until the stock change commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WarehouseStock s where s.companyId = :companyId and s.itemId = :itemId "
            + "and s.warehouseId = :warehouseId")
    Optional<WarehouseStock> lock(Long companyId, Long itemId, Long warehouseId);

    @Query("select s from WarehouseStock s where s.companyId = :companyId "
            + "and (:itemId is null or s.itemId = :itemId) and (:warehouseId is null or s.warehouseId = :warehouseId) "
            + "order by s.itemId, s.warehouseId")
    List<WarehouseStock> find(Long companyId, Long itemId, Long warehouseId);

    @Query("select coalesce(sum(s.quantity), 0) from WarehouseStock s where s.itemId = :itemId")
    BigDecimal sumByItem(Long itemId);

    /** [itemId, sum of quantity] for the items of a page. */
    @Query("select s.itemId, sum(s.quantity) from WarehouseStock s where s.itemId in :itemIds group by s.itemId")
    List<Object[]> sumByItems(Collection<Long> itemIds);

    /** [warehouseId, count] of stock rows at 0 or less of live items, per warehouse of the company. */
    @Query("select s.warehouseId, count(s) from WarehouseStock s, Item i where i.id = s.itemId "
            + "and s.companyId = :companyId and i.deleted = false and s.quantity <= 0 group by s.warehouseId")
    List<Object[]> outOfStockByWarehouse(Long companyId);

    boolean existsByWarehouseIdAndQuantityGreaterThan(Long warehouseId, BigDecimal quantity);
}
