package com.terp.erp.crm.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Long> {
    Optional<ContractType> findByIdAndDeletedFalse(Long id);

    List<ContractType> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalse(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
