package com.terp.erp.project.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BugRepository extends JpaRepository<Bug, Long> {
    Optional<Bug> findByIdAndDeletedFalse(Long id);

    List<Bug> findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(Long projectId);

    List<Bug> findByProjectIdAndStageIdAndDeletedFalseOrderByPositionAscIdAsc(Long projectId, Long stageId);

    boolean existsByStageIdAndDeletedFalse(Long stageId);

    @Query("select coalesce(max(b.position), 0) from Bug b where b.projectId = :project and b.stageId = :stage and b.deleted = false")
    int maxPosition(@Param("project") Long projectId, @Param("stage") Long stageId);
}
