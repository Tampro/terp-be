package com.terp.erp.accounting.coa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountCategoryRepository extends JpaRepository<AccountCategory, Long> {
    @Query("select c from AccountCategory c where c.companyId = :companyId and c.kind = :kind "
            + "and (:enabled is null or c.enabled = :enabled)")
    Page<AccountCategory> search(Long companyId, ECategoryKind kind, Boolean enabled, Pageable pageable);

    boolean existsByCompanyIdAndKindAndCodeAndIdNot(Long companyId, ECategoryKind kind, String code, Long id);

    boolean existsByGlAccountId(Long glAccountId);
}
