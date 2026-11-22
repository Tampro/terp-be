package com.terp.erp.accounting.coa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountTypeRepository extends JpaRepository<AccountType, Long> {
    @Query("select t from AccountType t where t.companyId = :companyId "
            + "and (:category is null or t.category = :category) and (:active is null or t.active = :active)")
    Page<AccountType> search(Long companyId, EAccountCategory category, Boolean active, Pageable pageable);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);
}
