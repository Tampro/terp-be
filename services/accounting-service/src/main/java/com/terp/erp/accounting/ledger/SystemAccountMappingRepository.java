package com.terp.erp.accounting.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemAccountMappingRepository extends JpaRepository<SystemAccountMapping, Long> {
    List<SystemAccountMapping> findByCompanyId(Long companyId);

    Optional<SystemAccountMapping> findByCompanyIdAndSystemKey(Long companyId, ESystemAccount systemKey);

    boolean existsByAccountId(Long accountId);
}
