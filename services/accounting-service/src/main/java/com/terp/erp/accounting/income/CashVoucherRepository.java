package com.terp.erp.accounting.income;

import com.terp.erp.accounting.coa.ECategoryKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface CashVoucherRepository extends JpaRepository<CashVoucher, Long> {
    @Query("select v from CashVoucher v where v.companyId = :companyId and v.kind = :kind and v.deleted = false "
            + "and (:categoryId is null or v.categoryId = :categoryId) and (:status is null or v.status = :status) "
            + "and (:bankAccountId is null or v.bankAccountId = :bankAccountId) "
            + "and (:from is null or v.date >= :from) and (:to is null or v.date <= :to)")
    Page<CashVoucher> search(Long companyId, ECategoryKind kind, Long categoryId, EVoucherStatus status,
                             Long bankAccountId, LocalDate from, LocalDate to, Pageable pageable);

    Optional<CashVoucher> findByIdAndDeletedFalse(Long id);

    boolean existsByCategoryId(Long categoryId);
}
