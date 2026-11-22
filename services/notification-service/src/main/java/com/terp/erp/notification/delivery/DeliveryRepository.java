package com.terp.erp.notification.delivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    @Query("select d from Delivery d where d.companyId = :companyId"
            + " and (:status is null or d.status = :status)"
            + " and (:channel is null or d.channel = :channel)"
            + " and (:eventType is null or d.eventType = :eventType)"
            + " and (:from is null or d.createdDate >= :from)"
            + " and (:to is null or d.createdDate < :to)")
    Page<Delivery> search(@Param("companyId") Long companyId, @Param("status") Delivery.EStatus status,
                          @Param("channel") Delivery.EChannel channel, @Param("eventType") String eventType,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<Delivery> findByEventIdOrderByIdAsc(String eventId);

    /** Waiting deliveries whose retry time has come, oldest first. */
    @Query("select d.id from Delivery d where d.status = com.terp.erp.notification.delivery.Delivery.EStatus.PENDING"
            + " and d.nextAttemptAt <= :now order by d.nextAttemptAt asc, d.id asc")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);
}
