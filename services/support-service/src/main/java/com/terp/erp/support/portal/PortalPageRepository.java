package com.terp.erp.support.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortalPageRepository extends JpaRepository<PortalPage, Long> {
    Optional<PortalPage> findByIdAndDeletedFalse(Long id);

    Optional<PortalPage> findByCompanyIdAndSlugAndDeletedFalse(Long companyId, String slug);

    List<PortalPage> findByCompanyIdAndDeletedFalseOrderByTitleAsc(Long companyId);
}
