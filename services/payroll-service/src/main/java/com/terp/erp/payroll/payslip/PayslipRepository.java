package com.terp.erp.payroll.payslip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    /** keyword on the copied employee name / no. / email; the caller escapes with !. */
    String FILTERS = "from Payslip p where p.companyId = :companyId "
            + "and (:year is null or p.periodYear = :year) and (:month is null or p.periodMonth = :month) "
            + "and (:employeeId is null or p.employeeId = :employeeId) "
            + "and (:kw = '' or lower(p.employeeName) like :kw escape '!' or lower(p.employeeNo) like :kw escape '!' "
            + "or lower(p.employeeEmail) like :kw escape '!')";

    @Query(value = "select p " + FILTERS + " and (:status is null or p.status = :status)",
            countQuery = "select count(p) " + FILTERS + " and (:status is null or p.status = :status)")
    Page<Payslip> search(Long companyId, Integer year, Integer month, Long employeeId, Payslip.EStatus status,
                         String kw, Pageable pageable);

    @Query("select p.status, count(p) " + FILTERS + " group by p.status")
    List<Object[]> countByStatus(Long companyId, Integer year, Integer month, Long employeeId, String kw);

    boolean existsByRunId(Long runId);

    boolean existsByEntryId(Long entryId);

    @Query("select p.entryId from Payslip p where p.entryId in :entryIds")
    Set<Long> entriesWithPayslip(Collection<Long> entryIds);
}
