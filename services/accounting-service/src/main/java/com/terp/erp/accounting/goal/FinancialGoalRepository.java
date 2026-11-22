package com.terp.erp.accounting.goal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    Optional<FinancialGoal> findByIdAndDeletedFalse(Long id);

    @Query("select g from FinancialGoal g where g.companyId = :companyId and g.deleted = false "
            + "and (:categoryId is null or g.categoryId = :categoryId) and (:status is null or g.status = :status) "
            + "and (:type is null or g.type = :type)")
    Page<FinancialGoal> search(Long companyId, Long categoryId, EGoalStatus status, EGoalType type,
                               Pageable pageable);
}
