package com.terp.erp.order.mileage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MileageLotRepository extends JpaRepository<MileageLot, Long> {
    /** The customer's spendable lots, soonest expiry first, locked until the order's transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MileageLot l where l.companyId = :company and l.customerId = :customer and l.remaining > 0 "
            + "and l.expiresAt > :now order by l.expiresAt, l.id")
    List<MileageLot> lockOpen(@Param("company") Long companyId, @Param("customer") Long customerId, @Param("now") Instant now);

    @Query("select coalesce(sum(l.remaining), 0) from MileageLot l where l.companyId = :company "
            + "and l.customerId = :customer and l.remaining > 0 and l.expiresAt > :now")
    long balance(@Param("company") Long companyId, @Param("customer") Long customerId, @Param("now") Instant now);

    @Query("select coalesce(sum(l.remaining), 0) from MileageLot l where l.companyId = :company "
            + "and l.customerId = :customer and l.remaining > 0 and l.expiresAt > :now and l.expiresAt <= :until")
    long expiring(@Param("company") Long companyId, @Param("customer") Long customerId, @Param("now") Instant now,
                  @Param("until") Instant until);

    @Query("select l.id from MileageLot l where l.remaining > 0 and l.expiresAt <= :now order by l.id")
    List<Long> overdue(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MileageLot l where l.id = :id")
    Optional<MileageLot> lockById(@Param("id") Long id);

    boolean existsByEarnOrderId(Long earnOrderId);
}
