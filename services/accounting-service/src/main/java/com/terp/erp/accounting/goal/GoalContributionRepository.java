package com.terp.erp.accounting.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {
    List<GoalContribution> findByGoalIdOrderByDateDescIdDesc(Long goalId);
}
