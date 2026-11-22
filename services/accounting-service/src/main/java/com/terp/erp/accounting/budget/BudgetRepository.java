package com.terp.erp.accounting.budget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByIdAndDeletedFalse(Long id);

    @Query("select b from Budget b where b.companyId = :companyId and b.deleted = false "
            + "and (:periodId is null or b.periodId = :periodId) and (:status is null or b.status = :status) "
            + "and (:type is null or b.type = :type)")
    Page<Budget> search(Long companyId, Long periodId, EBudgetStatus status, EBudgetType type, Pageable pageable);

    List<Budget> findByPeriodIdAndDeletedFalseOrderByIdAsc(Long periodId);

    boolean existsByPeriodIdAndDeletedFalse(Long periodId);
}
