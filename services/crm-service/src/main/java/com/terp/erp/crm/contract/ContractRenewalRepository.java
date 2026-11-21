package com.terp.erp.crm.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractRenewalRepository extends JpaRepository<ContractRenewal, Long> {
    Optional<ContractRenewal> findByIdAndContractIdAndDeletedFalse(Long id, Long contractId);

    List<ContractRenewal> findByContractIdAndDeletedFalseOrderByIdAsc(Long contractId);
}
