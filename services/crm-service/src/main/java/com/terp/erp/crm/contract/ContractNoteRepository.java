package com.terp.erp.crm.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractNoteRepository extends JpaRepository<ContractNote, Long> {
    Optional<ContractNote> findByIdAndContractIdAndDeletedFalse(Long id, Long contractId);

    List<ContractNote> findByContractIdAndDeletedFalseOrderByIdAsc(Long contractId);
}
