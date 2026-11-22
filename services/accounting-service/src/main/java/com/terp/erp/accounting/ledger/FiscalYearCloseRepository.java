package com.terp.erp.accounting.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FiscalYearCloseRepository extends JpaRepository<FiscalYearClose, Long> {
    boolean existsByCompanyIdAndFiscalYear(Long companyId, int fiscalYear);

    Page<FiscalYearClose> findByCompanyId(Long companyId, Pageable pageable);

    /** Latest closed year of the company, null when none. */
    @Query("select max(c.fiscalYear) from FiscalYearClose c where c.companyId = :companyId")
    Integer maxClosedYear(Long companyId);
}
