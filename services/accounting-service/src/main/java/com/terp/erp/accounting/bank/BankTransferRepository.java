package com.terp.erp.accounting.bank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface BankTransferRepository extends JpaRepository<BankTransfer, Long> {
    @Query("select t from BankTransfer t where t.companyId = :companyId and t.deleted = false "
            + "and (:status is null or t.status = :status) "
            + "and (:accountId is null or t.fromAccountId = :accountId or t.toAccountId = :accountId) "
            + "and (:from is null or t.date >= :from) and (:to is null or t.date <= :to)")
    Page<BankTransfer> search(Long companyId, ETransferStatus status, Long accountId, LocalDate from, LocalDate to,
                              Pageable pageable);

    Optional<BankTransfer> findByIdAndDeletedFalse(Long id);
}
