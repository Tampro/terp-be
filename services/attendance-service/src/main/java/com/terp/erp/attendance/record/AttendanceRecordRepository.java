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

    /** Auto clock-out: open records (clock-in, no clock-out) with a shift, of days in [from, to]. */
    List<AttendanceRecord> findByClockInIsNotNullAndClockOutIsNullAndShiftIdIsNotNullAndWorkDateBetween(LocalDate from,
                                                                                                     LocalDate to);

    /**
     * Closes an open record. "clock_out is null" makes it idempotent: a second instance of the job, or the employee
     * clocking out meanwhile, leaves the row alone (0 rows updated).
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update AttendanceRecord r set r.clockOut = :clockOut, "
            + "r.autoClockedOut = true, r.totalHours = :totalHours, r.breakHours = :breakHours, "
            + "r.overtimeHours = :overtimeHours, r.overtimeAmount = :overtimeAmount, "
            + "r.earlyDeparture = :earlyDeparture, r.status = :status, r.updatedDate = :now "
            + "where r.id = :id and r.clockOut is null and r.clockIn is not null")
    int autoClockOut(Long id, java.time.LocalTime clockOut, java.math.BigDecimal totalHours,
                     java.math.BigDecimal breakHours, java.math.BigDecimal overtimeHours,
                     java.math.BigDecimal overtimeAmount, boolean earlyDeparture, ERecordStatus status,
                     java.time.Instant now);

    /** Dashboard: rows of [workDate, status, count] of the company's records in [from, to]. */
    @org.springframework.data.jpa.repository.Query("select r.workDate, r.status, count(r) from AttendanceRecord r "
            + "where r.companyId = :companyId and r.workDate between :from and :to group by r.workDate, r.status")
    List<Object[]> countByDayAndStatus(Long companyId, LocalDate from, LocalDate to);
}
