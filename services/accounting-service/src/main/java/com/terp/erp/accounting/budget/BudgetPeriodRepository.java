package com.terp.erp.accounting.budget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BudgetPeriodRepository extends JpaRepository<BudgetPeriod, Long> {
    Optional<BudgetPeriod> findByIdAndDeletedFalse(Long id);

    @Query("select p from BudgetPeriod p where p.companyId = :companyId and p.deleted = false "
            + "and (:status is null or p.status = :status) "
            + "and (:financialYear is null or p.financialYear = :financialYear)")
    Page<BudgetPeriod> search(Long companyId, EBudgetPeriodStatus status, Integer financialYear, Pageable pageable);
}
