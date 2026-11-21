package com.terp.erp.payroll.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    /** keyword on the title; the caller escapes with !. */
    String FILTERS = "from PayrollRun r where r.companyId = :companyId and r.deleted = false "
            + "and (:kw = '' or lower(r.title) like :kw escape '!')";

    @Query(value = "select r " + FILTERS + " and (:status is null or r.status = :status)",
            countQuery = "select count(r) " + FILTERS + " and (:status is null or r.status = :status)")
    Page<PayrollRun> search(Long companyId, PayrollRun.EStatus status, String kw, Pageable pageable);

    @Query("select r.status, count(r) " + FILTERS + " group by r.status")
    List<Object[]> countByStatus(Long companyId, String kw);

    Optional<PayrollRun> findByIdAndDeletedFalse(Long id);

    /** Dashboard: runs of a status paid within [from, to]. */
    List<PayrollRun> findByCompanyIdAndStatusAndDeletedFalseAndPayDateBetween(Long companyId, PayrollRun.EStatus status,
                                                                       java.time.LocalDate from, java.time.LocalDate to);
}
