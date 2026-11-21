package com.terp.erp.inventory.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    /**
     * Locking read on purpose: under REPEATABLE READ a plain read could answer from the transaction's snapshot and miss
     * movements another transaction committed meanwhile; a locking read sees the latest committed rows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockMovement> findTop1ByCompanyIdAndSourceTypeAndSourceId(Long companyId, String sourceType, Long sourceId);
}
