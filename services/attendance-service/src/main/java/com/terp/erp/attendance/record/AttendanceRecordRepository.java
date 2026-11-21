package com.terp.erp.attendance.record;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByCompanyIdAndEmployeeIdInAndWorkDateBetween(Long companyId, Collection<Long> employeeIds,
                                                                           LocalDate from, LocalDate to);

    org.springframework.data.domain.Page<AttendanceRecord> findByCompanyIdAndEmployeeIdOrderByWorkDateDesc(
            Long companyId, Long employeeId, org.springframework.data.domain.Pageable pageable);

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    boolean existsByEmployeeIdAndWorkDateAndIdNot(Long employeeId, LocalDate workDate, Long id);

    /** Dashboard: rows of [workDate, status, count] of the company's records in [from, to]. */
    @org.springframework.data.jpa.repository.Query("select r.workDate, r.status, count(r) from AttendanceRecord r "
            + "where r.companyId = :companyId and r.workDate between :from and :to group by r.workDate, r.status")
    List<Object[]> countByDayAndStatus(Long companyId, LocalDate from, LocalDate to);
}
