package com.terp.erp.commerce.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequence.Key> {
    /** Two first-of-the-month documents both insert; the loser's insert is ignored instead of failing on the PK. */
    @Modifying
    @Query(value = "insert ignore into document_sequence (company_id, prefix, period, next_value) "
            + "values (:companyId, :prefix, :period, 1)", nativeQuery = true)
    void ensureRow(Long companyId, String prefix, String period);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.key = :key")
    Optional<DocumentSequence> lock(DocumentSequence.Key key);
}
