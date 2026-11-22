package com.terp.erp.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    /**
     * Due rows, locked until the caller's transaction ends. SKIP LOCKED (MySQL 8) lets several instances of a service
     * relay at the same time without publishing a row twice or waiting on each other.
     */
    @Query(value = "SELECT * FROM outbox_event WHERE status = 'NEW' AND next_attempt_at <= :now "
            + "ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
