package com.terp.erp.accounting.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    Optional<JournalEntry> findByCompanyIdAndSourceTypeAndSourceId(Long companyId, String sourceType, Long sourceId);

    @Query("select e from JournalEntry e where e.companyId = :companyId "
            + "and (:sourceType is null or e.sourceType = :sourceType) "
            + "and (:from is null or e.entryDate >= :from) and (:to is null or e.entryDate <= :to)")
    Page<JournalEntry> search(Long companyId, String sourceType, LocalDate from, LocalDate to, Pageable pageable);

    @Query("select count(l) > 0 from JournalEntry e join e.lines l where l.accountId = :accountId")
    boolean existsLineOnAccount(Long accountId);
}
