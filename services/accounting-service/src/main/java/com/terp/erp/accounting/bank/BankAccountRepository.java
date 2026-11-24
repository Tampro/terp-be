package com.terp.erp.accounting.bank;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    /** keyword on holder, bank and number; active / type null = all; the caller escapes with !. */
    @Query("select a from BankAccount a where a.companyId = :companyId and a.deleted = false "
            + "and (:active is null or a.active = :active) and (:type is null or a.accountType = :type) "
            + "and (:kw = '' "
            + "or lower(a.holderName) like :kw escape '!' or lower(a.bankName) like :kw escape '!' "
            + "or lower(a.accountNumber) like :kw escape '!')")
    Page<BankAccount> search(Long companyId, Boolean active, EBankAccountType type, String kw, Pageable pageable);

    Optional<BankAccount> findByIdAndDeletedFalse(Long id);

    /** SELECT ... FOR UPDATE: concurrent balance changes on one account wait here, so none is lost. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id = :id and a.deleted = false")
    Optional<BankAccount> lockById(Long id);

    boolean existsByGlAccountId(Long glAccountId);

    boolean existsByCompanyIdAndAccountNumberAndDeletedFalseAndIdNot(Long companyId, String accountNumber, Long id);
}
