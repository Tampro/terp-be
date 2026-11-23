package com.terp.erp.attendance.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {
    /** employeeIds = employees whose name matched the keyword (a placeholder id when there is no keyword). */
    @Query("select a from LeaveApplication a where a.companyId = :companyId "
            + "and (:status is null or a.status = :status) "
            + "and (:employeeId is null or a.employeeId = :employeeId) "
            + "and (:leaveTypeId is null or a.leaveTypeId = :leaveTypeId) "
            + "and (:from is null or a.endDate >= :from) and (:to is null or a.startDate <= :to) "
            + "and (:anyEmployee = true or a.employeeId in :employeeIds)")
    Page<LeaveApplication> search(Long companyId, ERegularizationStatus status, Long employeeId, Long leaveTypeId,
                                  LocalDate from, LocalDate to, boolean anyEmployee, Collection<Long> employeeIds,
                                  Pageable pageable);

    /** Applications of the employee overlapping [from, to] that block the days (pending or approved). */
    @Query("select count(a) > 0 from LeaveApplication a where a.employeeId = :employeeId and a.id <> :id "
            + "and a.status <> com.terp.erp.attendance.regularization.ERegularizationStatus.REJECTED "
            + "and a.startDate <= :to and a.endDate >= :from")
    boolean overlaps(Long employeeId, LocalDate from, LocalDate to, Long id);

    /** Days of the employee's pending applications of a type in a year (reserved on the balance). */
    @Query("select coalesce(sum(a.totalDays), 0) from LeaveApplication a where a.employeeId = :employeeId "
            + "and a.leaveTypeId = :leaveTypeId and a.id <> :id "
            + "and a.status = com.terp.erp.attendance.regularization.ERegularizationStatus.PENDING "
            + "and a.startDate >= :yearStart and a.startDate <= :yearEnd")
    long pendingDays(Long employeeId, Long leaveTypeId, LocalDate yearStart, LocalDate yearEnd, Long id);

    /** Approved days per employee and type in a year: the balances' Used, recomputed by Sync. */
    @Query("select a.employeeId, a.leaveTypeId, sum(a.totalDays) from LeaveApplication a where a.companyId = :companyId "
            + "and a.status = com.terp.erp.attendance.regularization.ERegularizationStatus.APPROVED "
            + "and a.startDate >= :yearStart and a.startDate <= :yearEnd group by a.employeeId, a.leaveTypeId")
    List<Object[]> approvedDays(Long companyId, LocalDate yearStart, LocalDate yearEnd);

    /** Approved applications of the employees overlapping [from, to] (payroll summary). */
    @Query("select a from LeaveApplication a where a.companyId = :companyId and a.employeeId in :employeeIds "
            + "and a.status = com.terp.erp.attendance.regularization.ERegularizationStatus.APPROVED "
            + "and a.startDate <= :to and a.endDate >= :from")
    List<LeaveApplication> approvedOverlapping(Long companyId, java.util.Collection<Long> employeeIds, LocalDate from,
                                               LocalDate to);

    boolean existsByLeaveTypeId(Long leaveTypeId);

    // ---- dashboard
    long countByCompanyIdAndStatus(Long companyId, ERegularizationStatus status);

    /** Rows of [status, count] of the applications starting in [from, to]. */
    @Query("select a.status, count(a) from LeaveApplication a where a.companyId = :companyId "
            + "and a.startDate >= :from and a.startDate <= :to group by a.status")
    List<Object[]> countByStatusStartingBetween(Long companyId, LocalDate from, LocalDate to);

    /** Applications of the status covering the date. */
    @Query("select a from LeaveApplication a where a.companyId = :companyId and a.status = :status "
            + "and a.startDate <= :date and a.endDate >= :date order by a.startDate")
    List<LeaveApplication> covering(Long companyId, ERegularizationStatus status, LocalDate date);

    long countByCompanyIdAndEmployeeIdAndStatus(Long companyId, Long employeeId, ERegularizationStatus status);

    List<LeaveApplication> findTop5ByCompanyIdAndEmployeeIdOrderByCreatedDateDescIdDesc(Long companyId, Long employeeId);
}
