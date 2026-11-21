package com.terp.erp.meeting.zoom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ZoomMeetingRepository extends JpaRepository<ZoomMeeting, Long> {
    /** Meetings starting in [from, to], by start. */
    @Query("select z from ZoomMeeting z where z.companyId = :companyId and z.deleted = false "
            + "and z.startDate between :from and :to and (:status is null or z.status = :status) "
            + "order by z.startDate, z.startTime, z.id")
    List<ZoomMeeting> between(Long companyId, LocalDate from, LocalDate to, ZoomMeeting.EStatus status);

    Optional<ZoomMeeting> findByIdAndDeletedFalse(Long id);
}
