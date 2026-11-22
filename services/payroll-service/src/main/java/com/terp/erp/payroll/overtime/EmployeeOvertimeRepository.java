package com.terp.erp.payroll.overtime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeOvertimeRepository extends JpaRepository<EmployeeOvertime, Long> {
    @Query("select o from EmployeeOvertime o where o.companyId = :companyId and o.employeeId = :employeeId "
            + "and o.deleted = false and (:status is null or o.status = :status)")
    Page<EmployeeOvertime> search(Long companyId, Long employeeId, EmployeeOvertime.EStatus status, Pageable pageable);

    Optional<EmployeeOvertime> findByIdAndDeletedFalse(Long id);

    /** APPROVED, not yet paid, overlapping [from, to]. */
    @Query("select o from EmployeeOvertime o where o.companyId = :companyId and o.deleted = false "
            + "and o.status = com.terp.erp.payroll.overtime.EmployeeOvertime.EStatus.APPROVED and o.paidRunId is null "
            + "and o.employeeId in :employeeIds and o.startDate <= :to and o.endDate >= :from")
    List<EmployeeOvertime> payableIn(Long companyId, Collection<Long> employeeIds, LocalDate from, LocalDate to);

    List<EmployeeOvertime> findByPaidRunId(Long runId);

    List<EmployeeOvertime> findByPaidRunIdAndEmployeeId(Long runId, Long employeeId);
}
