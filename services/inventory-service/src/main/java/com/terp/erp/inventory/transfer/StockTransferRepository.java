package com.terp.erp.inventory.transfer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    @Query("select t from StockTransfer t where t.companyId = :companyId and t.deleted = false "
            + "and (:fromId is null or t.fromWarehouseId = :fromId) and (:toId is null or t.toWarehouseId = :toId) "
            + "and (:itemId is null or t.itemId = :itemId) "
            + "and (:dateFrom is null or t.transferDate >= :dateFrom) and (:dateTo is null or t.transferDate <= :dateTo)")
    Page<StockTransfer> search(Long companyId, Long fromId, Long toId, Long itemId, LocalDate dateFrom,
                               LocalDate dateTo, Pageable pageable);

    Optional<StockTransfer> findByIdAndDeletedFalse(Long id);
}
