package com.terp.erp.crm.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractAttachmentRepository extends JpaRepository<ContractAttachment, Long> {
    Optional<ContractAttachment> findByIdAndContractIdAndDeletedFalse(Long id, Long contractId);

    List<ContractAttachment> findByContractIdAndDeletedFalseOrderByIdAsc(Long contractId);
}
