package com.terp.erp.iam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.iam.model.Plan;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByIdAndDeletedFalse(Long id);

    Optional<Plan> findFirstByDefaultPlanTrueAndDeletedFalse();

    List<Plan> findByDeletedFalseOrderByMonthlyPriceAsc();

    List<Plan> findByEnabledTrueAndDeletedFalseOrderByMonthlyPriceAsc();

    boolean existsByCodeAndDeletedFalse(String code);
}
