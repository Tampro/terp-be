package com.terp.erp.accounting.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalMilestoneRepository extends JpaRepository<GoalMilestone, Long> {
    List<GoalMilestone> findByGoalIdOrderByTargetDateAscIdAsc(Long goalId);
}
