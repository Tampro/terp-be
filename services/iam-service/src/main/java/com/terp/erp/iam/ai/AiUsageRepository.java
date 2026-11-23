package com.terp.erp.iam.ai;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
    Page<AiUsage> findByCompanyId(Long companyId, Pageable pageable);
}
