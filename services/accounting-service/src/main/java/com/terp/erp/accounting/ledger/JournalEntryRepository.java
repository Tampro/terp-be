package com.terp.erp.accounting.ledger;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    Optional<JournalEntry> findByCompanyIdAndSourceTypeAndSourceId(Long companyId, String sourceType, Long sourceId);

    @Query("select e from JournalEntry e where e.companyId = :companyId "
            + "and (:sourceType is null or e.sourceType = :sourceType) "
            + "and (:type is null or e.type = :type) and (:status is null or e.status = :status) "
            + "and (:from is null or e.entryDate >= :from) and (:to is null or e.entryDate <= :to)")
    Page<JournalEntry> search(Long companyId, String sourceType, EJournalType type, EJournalStatus status,
                              LocalDate from, LocalDate to, Pageable pageable);

    @Query("select count(l) > 0 from JournalEntry e join e.lines l where l.accountId = :accountId")
    boolean existsLineOnAccount(Long accountId);

    /** SELECT ... FOR UPDATE: two posts / reversals of one entry at once cannot both pass the status check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from JournalEntry e where e.id = :id")
    Optional<JournalEntry> lockById(Long id);

    long countByCompanyIdAndTypeAndStatusAndEntryDateBetween(Long companyId, EJournalType type,
                                                             EJournalStatus status, LocalDate from, LocalDate to);

    @Query("select new com.terp.erp.accounting.ledger.LedgerLine(e.id, e.number, e.entryDate, e.reference, "
            + "e.description, e.sourceType, e.sourceId, l.accountId, l.description, l.debit, l.credit) "
            + "from JournalEntry e join e.lines l where e.companyId = :companyId and e.status in :statuses "
            + "and e.entryDate >= :from and e.entryDate <= :to order by e.entryDate, e.id, l.id")
    List<LedgerLine> lines(Long companyId, Collection<EJournalStatus> statuses, LocalDate from, LocalDate to);

    @Query("select new com.terp.erp.accounting.ledger.LedgerLine(e.id, e.number, e.entryDate, e.reference, "
            + "e.description, e.sourceType, e.sourceId, l.accountId, l.description, l.debit, l.credit) "
            + "from JournalEntry e join e.lines l where e.companyId = :companyId and e.status in :statuses "
            + "and l.accountId = :accountId and e.entryDate >= :from and e.entryDate <= :to "
            + "order by e.entryDate, e.id, l.id")
    List<LedgerLine> accountLines(Long companyId, Long accountId, Collection<EJournalStatus> statuses,
                                  LocalDate from, LocalDate to);

    /** Totals per account of counted lines dated before {@code before} (exclusive). */
    @Query("select new com.terp.erp.accounting.ledger.AccountSum(l.accountId, sum(l.debit), sum(l.credit)) "
            + "from JournalEntry e join e.lines l where e.companyId = :companyId and e.status in :statuses "
            + "and e.entryDate < :before group by l.accountId")
    List<AccountSum> sumsBefore(Long companyId, Collection<EJournalStatus> statuses, LocalDate before);

    /** Totals per account of counted lines on the given accounts dated from..to (both inclusive). */
    @Query("select new com.terp.erp.accounting.ledger.AccountSum(l.accountId, sum(l.debit), sum(l.credit)) "
            + "from JournalEntry e join e.lines l where e.companyId = :companyId and e.status in :statuses "
            + "and l.accountId in :accountIds and e.entryDate >= :from and e.entryDate <= :to "
            + "group by l.accountId")
    List<AccountSum> sumsBetween(Long companyId, Collection<Long> accountIds, Collection<EJournalStatus> statuses,
                                 LocalDate from, LocalDate to);
}
