package com.terp.erp.accounting.note;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountNoteRepository extends JpaRepository<AccountNote, Long> {
    Optional<AccountNote> findByIdAndDeletedFalse(Long id);

    /** Held until the transaction ends: approve, and the balance taken by a clearing payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from AccountNote n where n.id = :id and n.deleted = false")
    Optional<AccountNote> lockById(@Param("id") Long id);

    Optional<AccountNote> findByCompanyIdAndKindAndReturnKey(Long companyId, ENoteKind kind, Long returnKey);

    @Query("select n from AccountNote n where n.companyId = :companyId and n.kind = :kind and n.deleted = false "
            + "and (:partyId is null or n.partyId = :partyId) and (:status is null or n.status = :status) "
            + "and (:from is null or n.noteDate >= :from) and (:to is null or n.noteDate <= :to)")
    Page<AccountNote> search(@Param("companyId") Long companyId, @Param("kind") ENoteKind kind,
                             @Param("partyId") Long partyId, @Param("status") ENoteStatus status,
                             @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    /** Notes a party sees in its portal: approved or later, never drafts. */
    @Query("select n from AccountNote n where n.companyId = :companyId and n.kind = :kind and n.deleted = false "
            + "and n.partyId = :partyId and n.status <> com.terp.erp.accounting.note.ENoteStatus.DRAFT")
    Page<AccountNote> issuedToParty(@Param("companyId") Long companyId, @Param("kind") ENoteKind kind,
                                    @Param("partyId") Long partyId, Pageable pageable);

    /** Approved notes in [from, to] (any status but DRAFT), for the customer / vendor report. */
    @Query("select n from AccountNote n where n.companyId = :companyId and n.kind = :kind and n.deleted = false "
            + "and n.partyId = :partyId and n.status <> com.terp.erp.accounting.note.ENoteStatus.DRAFT "
            + "and n.noteDate between :from and :to order by n.noteDate, n.id")
    List<AccountNote> approvedBetween(@Param("companyId") Long companyId, @Param("kind") ENoteKind kind,
                                      @Param("partyId") Long partyId, @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /** Unapplied balance per party: rows of [partyId, sum(balance)]. */
    @Query("select n.partyId, sum(n.balance) from AccountNote n where n.companyId = :companyId and n.kind = :kind "
            + "and n.deleted = false and n.status in (com.terp.erp.accounting.note.ENoteStatus.APPROVED, "
            + "com.terp.erp.accounting.note.ENoteStatus.PARTIAL) group by n.partyId")
    List<Object[]> availableByParty(@Param("companyId") Long companyId, @Param("kind") ENoteKind kind);
}
