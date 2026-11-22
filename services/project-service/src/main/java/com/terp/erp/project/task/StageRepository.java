package com.terp.erp.project.task;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.project.project.ProjectEnums.EStageKind;

import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, Long> {
    List<Stage> findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(Long companyId, EStageKind kind);

    Optional<Stage> findByIdAndDeletedFalse(Long id);
}
