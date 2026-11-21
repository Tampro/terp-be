package com.terp.erp.employee.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface AnnouncementViewRepository extends JpaRepository<AnnouncementView, Long> {
    /** Records the first view only; a second open, also a concurrent one, is ignored by the unique key. */
    @Modifying
    @Query(value = "INSERT IGNORE INTO announcement_view (announcement_id, employee_id, viewed_at) "
            + "VALUES (:announcementId, :employeeId, :at)", nativeQuery = true)
    int recordFirstView(Long announcementId, Long employeeId, Instant at);

    long countByAnnouncementId(Long announcementId);
}
