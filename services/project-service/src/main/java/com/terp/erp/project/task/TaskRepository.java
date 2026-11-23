package com.terp.erp.project.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.project.project.ProjectEnums.EPriority;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByIdAndDeletedFalse(Long id);

    List<Task> findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(Long projectId);

    List<Task> findByProjectIdAndStageIdAndDeletedFalseOrderByPositionAscIdAsc(Long projectId, Long stageId);

    boolean existsByStageIdAndDeletedFalse(Long stageId);

    boolean existsByMilestoneIdAndDeletedFalse(Long milestoneId);

    long countByProjectIdAndDeletedFalse(Long projectId);

    long countByProjectIdAndStageIdAndDeletedFalse(Long projectId, Long stageId);

    @Query("select coalesce(max(t.position), 0) from Task t where t.projectId = :project and t.stageId = :stage and t.deleted = false")
    int maxPosition(@Param("project") Long projectId, @Param("stage") Long stageId);

    /** List view filters (FR-PRJ-004): priority, due date range, assignee; each null = no filter. */
    @Query("select t from Task t where t.projectId = :project and t.deleted = false"
            + " and (:priority is null or t.priority = :priority)"
            + " and (:from is null or t.dueDate >= :from)"
            + " and (:to is null or t.dueDate <= :to)"
            + " and (:assignee is null or :assignee member of t.assigneeIds)")
    Page<Task> search(@Param("project") Long projectId, @Param("priority") EPriority priority,
                      @Param("from") LocalDate from, @Param("to") LocalDate to,
                      @Param("assignee") Long assigneeId, Pageable pageable);

    @Query("select t from Task t where t.companyId = :company and t.deleted = false"
            + " and t.dueDate between :from and :to"
            + " and t.projectId in (select p.id from Project p where p.deleted = false)"
            + " order by t.dueDate asc, t.id asc")
    List<Task> dueBetween(@Param("company") Long companyId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Task> findByProjectIdInAndDeletedFalse(java.util.Collection<Long> projectIds);
}
