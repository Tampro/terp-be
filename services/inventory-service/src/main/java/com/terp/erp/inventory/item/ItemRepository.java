package com.terp.erp.inventory.item;

import com.terp.erp.inventory.item.Item.EItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    /** keyword on name or SKU; the caller escapes with !. */
    @Query("select i from Item i where i.companyId = :companyId and i.deleted = false "
            + "and (:kw = '' or lower(i.name) like :kw escape '!' or lower(i.sku) like :kw escape '!') "
            + "and (:type is null or i.type = :type) and (:categoryId is null or i.categoryId = :categoryId)")
    Page<Item> search(Long companyId, String kw, EItemType type, Long categoryId, Pageable pageable);

    Optional<Item> findByIdAndDeletedFalse(Long id);

    boolean existsByCompanyIdAndSkuIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String sku, Long id);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    boolean existsByTaxIdAndDeletedFalse(Long taxId);

    boolean existsByUnitIdAndDeletedFalse(Long unitId);
}
