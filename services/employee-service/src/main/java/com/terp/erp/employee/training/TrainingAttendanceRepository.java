package com.terp.erp.employee.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {
    List<TrainingAttendance> findBySessionId(Long sessionId);

    /** [sessionId, present count] for the Attendance column. */
    @Query("select a.sessionId, count(a) from TrainingAttendance a where a.sessionId in :sessionIds "
            + "and a.status = com.terp.erp.employee.training.TrainingAttendance.EStatus.PRESENT group by a.sessionId")
    List<Object[]> presentBySession(Collection<Long> sessionIds);
}
