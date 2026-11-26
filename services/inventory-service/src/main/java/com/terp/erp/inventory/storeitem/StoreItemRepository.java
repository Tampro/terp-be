package com.terp.erp.inventory.storeitem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every query filters deleted = false: deleted rows only exist to keep the history. */
public interface StoreItemRepository extends JpaRepository<StoreItem, Long> {
    Optional<StoreItem> findByIdAndDeletedFalse(Long id);

    boolean existsByCompanyIdAndWarehouseIdAndItemIdAndDeletedFalse(Long companyId, Long warehouseId, Long itemId);

    /** Live rows of the company whose item is live; keyword on item name or SKU (caller escapes with !). */
    @Query(value = "select s from StoreItem s, com.terp.erp.inventory.item.Item i where i.id = s.itemId "
            + "and s.companyId = :companyId and s.deleted = false and i.deleted = false "
            + "and (:warehouseId is null or s.warehouseId = :warehouseId) "
            + "and (:categoryId is null or i.categoryId = :categoryId) "
            + "and (:status is null or s.status = :status) "
            + "and (:kw = '' or lower(i.name) like :kw escape '!' or lower(i.sku) like :kw escape '!')",
            countQuery = "select count(s) from StoreItem s, com.terp.erp.inventory.item.Item i where i.id = s.itemId "
                    + "and s.companyId = :companyId and s.deleted = false and i.deleted = false "
                    + "and (:warehouseId is null or s.warehouseId = :warehouseId) "
                    + "and (:categoryId is null or i.categoryId = :categoryId) "
                    + "and (:status is null or s.status = :status) "
                    + "and (:kw = '' or lower(i.name) like :kw escape '!' or lower(i.sku) like :kw escape '!')")
    Page<StoreItem> search(Long companyId, Long warehouseId, Long categoryId, EStoreItemStatus status, String kw,
                           Pageable pageable);

    /** [itemId, warehouseId, quantity] of the stock rows of these items (one query per page). */
    @Query("select w.itemId, w.warehouseId, w.quantity from WarehouseStock w where w.companyId = :companyId "
            + "and w.itemId in :itemIds")
    List<Object[]> stockOf(Long companyId, Collection<Long> itemIds);

    /** Live rows of the company among these warehouses and items (bulk upsert). */
    @Query("select s from StoreItem s where s.companyId = :companyId and s.deleted = false "
            + "and s.warehouseId in :warehouseIds and s.itemId in :itemIds")
    List<StoreItem> findLive(Long companyId, Collection<Long> warehouseIds, Collection<Long> itemIds);
}
