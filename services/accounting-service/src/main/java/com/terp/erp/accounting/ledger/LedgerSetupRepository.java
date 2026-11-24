package com.terp.erp.accounting.ledger;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LedgerSetupRepository extends JpaRepository<LedgerSetup, Long> {
    /** Non-locking fast path: true once the company's chart is seeded (every call after the first). */
    boolean existsByCompanyIdAndSeededTrue(Long companyId);

    /**
     * Creates the row if missing. ON DUPLICATE KEY UPDATE (not INSERT IGNORE) takes an exclusive lock on an existing
     * row: INSERT IGNORE took a shared one, and two requests then deadlocked upgrading it in lockByCompanyId (MySQL
     * 1213, seen with parallel reads 2026-11-24).
     */
    @Modifying
    @Query(value = "insert into ledger_setup (company_id, seeded) values (:companyId, 0) "
            + "on duplicate key update company_id = company_id", nativeQuery = true)
    void ensureRow(Long companyId);

    /** SELECT ... FOR UPDATE: a second first use waits for the seed, then sees seeded = true. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LedgerSetup s where s.companyId = :companyId")
    Optional<LedgerSetup> lockByCompanyId(Long companyId);
}
