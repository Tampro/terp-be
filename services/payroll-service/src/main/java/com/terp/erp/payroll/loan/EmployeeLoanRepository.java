package com.terp.erp.payroll.loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {
    Page<EmployeeLoan> findByCompanyIdAndEmployeeIdAndDeletedFalse(Long companyId, Long employeeId, Pageable pageable);

    Optional<EmployeeLoan> findByIdAndDeletedFalse(Long id);

    boolean existsByLoanTypeIdAndDeletedFalse(Long loanTypeId);

    /** ACTIVE loans of the employees that have started by the period end and not ended before its start. */
    @Query("select l from EmployeeLoan l where l.companyId = :companyId and l.deleted = false "
            + "and l.status = com.terp.erp.payroll.loan.EmployeeLoan.EStatus.ACTIVE and l.employeeId in :employeeIds "
            + "and l.startDate <= :to and (l.endDate is null or l.endDate >= :from) order by l.startDate, l.id")
    List<EmployeeLoan> dueIn(Long companyId, Collection<Long> employeeIds, LocalDate from, LocalDate to);
}
