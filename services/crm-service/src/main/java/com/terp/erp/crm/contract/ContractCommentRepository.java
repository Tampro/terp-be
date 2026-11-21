package com.terp.erp.crm.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractCommentRepository extends JpaRepository<ContractComment, Long> {
    Optional<ContractComment> findByIdAndContractIdAndDeletedFalse(Long id, Long contractId);

    List<ContractComment> findByContractIdAndDeletedFalseOrderByIdAsc(Long contractId);
}
