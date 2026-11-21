package com.terp.erp.meeting.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    /** The meetings of a day, by start time. */
    @Query("select m from Meeting m where m.companyId = :companyId and m.deleted = false and m.meetingDate = :date "
            + "and (:status is null or m.status = :status) order by m.startTime, m.id")
    List<Meeting> ofDay(Long companyId, LocalDate date, Meeting.EStatus status);

    /** The meetings of a period, by date and start time (cancelled included). */
    @Query("select m from Meeting m where m.companyId = :companyId and m.deleted = false "
            + "and m.meetingDate between :from and :to order by m.meetingDate, m.startTime, m.id")
    List<Meeting> inRange(Long companyId, LocalDate from, LocalDate to);

    @Query("select m.status, count(m) from Meeting m where m.companyId = :companyId and m.deleted = false "
            + "and m.meetingDate = :date group by m.status")
    List<Object[]> countByStatus(Long companyId, LocalDate date);

    /** The calendar dots: meetings per date of [from, to]. */
    @Query("select m.meetingDate, count(m) from Meeting m where m.companyId = :companyId and m.deleted = false "
            + "and m.meetingDate between :from and :to group by m.meetingDate order by m.meetingDate")
    List<Object[]> countByDate(Long companyId, LocalDate from, LocalDate to);

    /** Another live meeting of the room overlapping [start, end) on that date. */
    @Query("select count(m) > 0 from Meeting m where m.roomId = :roomId and m.deleted = false "
            + "and m.status <> com.terp.erp.meeting.meeting.Meeting.EStatus.CANCELLED and m.meetingDate = :date "
            + "and m.startTime < :end and m.endTime > :start and m.id <> :id")
    boolean roomBusy(Long roomId, LocalDate date, LocalTime start, LocalTime end, Long id);

    @Query("select m.meetingTypeId, count(m) from Meeting m where m.deleted = false and m.meetingTypeId in :ids "
            + "group by m.meetingTypeId")
    List<Object[]> countByType(Collection<Long> ids);

    @Query("select m.roomId, count(m) from Meeting m where m.deleted = false and m.roomId in :ids group by m.roomId")
    List<Object[]> countByRoom(Collection<Long> ids);

    boolean existsByMeetingTypeIdAndDeletedFalse(Long meetingTypeId);

    boolean existsByRoomIdAndDeletedFalse(Long roomId);

    Optional<Meeting> findByIdAndDeletedFalse(Long id);
}
