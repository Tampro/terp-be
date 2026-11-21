package com.terp.erp.crm.setup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<Source, Long> {
    List<Source> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    Optional<Source> findByIdAndDeletedFalse(Long id);

    List<Source> findByIdIn(Collection<Long> ids);
}
