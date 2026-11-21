package com.terp.erp.inventory.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
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

    boolean existsByWarehouseIdAndQuantityGreaterThan(Long warehouseId, BigDecimal quantity);
}
