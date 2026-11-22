package com.terp.erp.accounting.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BalanceSheetRepository extends JpaRepository<BalanceSheet, Long> {
    @Query("select s from BalanceSheet s where s.companyId = :companyId "
            + "and (:year is null or s.financialYear = :year) and (:status is null or s.status = :status)")
    Page<BalanceSheet> search(Long companyId, Integer year, EBalanceSheetStatus status, Pageable pageable);
}
