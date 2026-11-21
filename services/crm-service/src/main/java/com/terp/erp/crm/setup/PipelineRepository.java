package com.terp.erp.crm.setup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PipelineRepository extends JpaRepository<Pipeline, Long> {
    List<Pipeline> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    Optional<Pipeline> findByIdAndDeletedFalse(Long id);

    boolean existsByCompanyIdAndNameAndDeletedFalse(Long companyId, String name);
}
