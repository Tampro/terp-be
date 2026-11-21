package com.terp.erp.commerce.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, DocumentSequence.Key> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.key = :key")
    Optional<DocumentSequence> lock(DocumentSequence.Key key);
}
