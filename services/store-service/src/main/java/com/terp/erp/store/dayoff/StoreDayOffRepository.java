package com.terp.erp.store.dayoff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StoreDayOffRepository extends JpaRepository<StoreDayOff, Long> {
    List<StoreDayOff> findByStoreIdAndOffDateGreaterThanEqualOrderByOffDate(Long storeId, LocalDate from);

    List<StoreDayOff> findByStoreIdAndOffDateBetweenOrderByOffDate(Long storeId, LocalDate from, LocalDate to);

    Optional<StoreDayOff> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndOffDate(Long storeId, LocalDate offDate);

    /** Off dates in [from, to): one query for the whole opensAt search. */
    @Query("select d.offDate from StoreDayOff d where d.storeId = :storeId and d.offDate >= :from and d.offDate < :to")
    List<LocalDate> findOffDates(Long storeId, LocalDate from, LocalDate to);
}
