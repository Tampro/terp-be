package com.terp.erp.accounting.bank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    Page<BankTransaction> findByBankAccountId(Long bankAccountId, Pageable pageable);

    Optional<BankTransaction> findByCompanyIdAndSourceTypeAndSourceId(Long companyId, String sourceType,
                                                                      Long sourceId);
}
