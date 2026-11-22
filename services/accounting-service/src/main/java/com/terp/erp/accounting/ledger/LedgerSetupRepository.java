package com.terp.erp.accounting.ledger;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LedgerSetupRepository extends JpaRepository<LedgerSetup, Long> {
    /** INSERT IGNORE so two first uses in one company at once don't fail. */
    @Modifying
    @Query(value = "insert ignore into ledger_setup (company_id, seeded) values (:companyId, 0)", nativeQuery = true)
    void ensureRow(Long companyId);

    /** SELECT ... FOR UPDATE: a second first use waits for the seed, then sees seeded = true. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LedgerSetup s where s.companyId = :companyId")
    Optional<LedgerSetup> lockByCompanyId(Long companyId);
}
