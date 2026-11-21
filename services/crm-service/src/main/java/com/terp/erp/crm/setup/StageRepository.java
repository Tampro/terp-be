package com.terp.erp.crm.setup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, Long> {
    List<Stage> findByPipelineIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(Long pipelineId, EStageKind kind);

    List<Stage> findByPipelineIdAndDeletedFalseOrderByKindAscPositionAscIdAsc(Long pipelineId);

    Optional<Stage> findByIdAndDeletedFalse(Long id);

    List<Stage> findByIdIn(Collection<Long> ids);

    @Query("select coalesce(max(s.position), 0) from Stage s where s.pipelineId = :pipelineId and s.kind = :kind and s.deleted = false")
    int maxPosition(@Param("pipelineId") Long pipelineId, @Param("kind") EStageKind kind);
}
