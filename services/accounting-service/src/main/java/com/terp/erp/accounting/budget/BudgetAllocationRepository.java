package com.terp.erp.accounting.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, Long> {
    List<BudgetAllocation> findByBudgetIdOrderByIdAsc(Long budgetId);

    /** id = -1 when creating. */
    boolean existsByBudgetIdAndAccountIdAndIdNot(Long budgetId, Long accountId, Long id);
}
