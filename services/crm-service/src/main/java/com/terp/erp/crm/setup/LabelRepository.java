package com.terp.erp.crm.setup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, Long> {
    List<Label> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    List<Label> findByPipelineIdAndDeletedFalseOrderByNameAsc(Long pipelineId);

    Optional<Label> findByIdAndDeletedFalse(Long id);

    List<Label> findByIdIn(Collection<Long> ids);
}
