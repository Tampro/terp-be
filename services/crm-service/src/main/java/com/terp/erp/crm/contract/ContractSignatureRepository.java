package com.terp.erp.crm.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {
    List<ContractSignature> findByContractIdOrderBySignedAtAsc(Long contractId);

    Optional<ContractSignature> findByContractIdAndSignerType(Long contractId, ContractSignature.ESignerType type);

    boolean existsByContractIdAndSignerType(Long contractId, ContractSignature.ESignerType type);

    boolean existsByContractId(Long contractId);

    long countByContractId(Long contractId);
}
