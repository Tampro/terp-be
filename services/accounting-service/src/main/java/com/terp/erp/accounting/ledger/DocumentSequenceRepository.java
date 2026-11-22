package com.terp.erp.accounting.ledger;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {
    @Modifying
    @Query(value = "insert ignore into accounting_sequence (company_id, seq_key, last_number) "
            + "values (:companyId, :seqKey, 0)", nativeQuery = true)
    void ensureRow(Long companyId, String seqKey);

    /** SELECT ... FOR UPDATE: two documents of one company and period never get one number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.companyId = :companyId and s.seqKey = :seqKey")
    Optional<DocumentSequence> lock(Long companyId, String seqKey);
}
