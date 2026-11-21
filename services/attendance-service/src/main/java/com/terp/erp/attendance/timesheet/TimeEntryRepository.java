package com.terp.erp.attendance.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    @Query("select t from TimeEntry t where t.companyId = :companyId and t.workDate between :from and :to "
            + "and (:status is null or t.status = :status) and t.employeeId in :employeeIds "
            + "order by t.workDate asc, t.id asc")
    List<TimeEntry> forEmployees(Long companyId, Collection<Long> employeeIds, LocalDate from, LocalDate to,
                                 ERegularizationStatus status);

    /** [status, count] of the status tabs: entries of [from, to] of the listed employees. */
    @Query("select t.status, count(t) from TimeEntry t where t.companyId = :companyId and t.workDate between :from "
            + "and :to and t.employeeId in :employeeIds group by t.status")
    List<Object[]> countByStatus(Long companyId, Collection<Long> employeeIds, LocalDate from, LocalDate to);

    @Query("select t from TimeEntry t where t.companyId = :companyId and t.workDate between :from and :to "
            + "and (:employeeId is null or t.employeeId = :employeeId) and (:status is null or t.status = :status) "
            + "order by t.workDate asc, t.employeeId asc, t.id asc")
    List<TimeEntry> export(Long companyId, Long employeeId, LocalDate from, LocalDate to, ERegularizationStatus status);

    List<TimeEntry> findByCompanyIdAndEmployeeIdAndWorkDateOrderByIdAsc(Long companyId, Long employeeId,
                                                                       LocalDate workDate);

    @Query("select coalesce(sum(t.hours), 0) from TimeEntry t where t.employeeId = :employeeId and t.workDate = :date "
            + "and t.id <> :id")
    BigDecimal hoursOfDay(Long employeeId, LocalDate date, Long id);
}
