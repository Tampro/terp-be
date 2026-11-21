package com.terp.erp.inventory.item;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {
    List<ItemImage> findByItemIdOrderBySortOrderAsc(Long itemId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ItemImage i where i.itemId = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);
}
