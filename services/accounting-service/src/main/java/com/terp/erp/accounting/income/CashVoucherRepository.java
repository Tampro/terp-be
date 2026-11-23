package com.terp.erp.accounting.income;

import java.util.List;
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

    /** [month, sum of amount] of POSTED vouchers of a kind dated in [from, to] (account dashboard). */
    @Query("select extract(month from v.date), coalesce(sum(v.amount), 0) from CashVoucher v where v.companyId = :companyId "
            + "and v.kind = :kind and v.deleted = false and v.status = com.terp.erp.accounting.income.EVoucherStatus.POSTED "
            + "and v.date between :from and :to group by extract(month from v.date)")
    List<Object[]> postedByMonth(Long companyId, ECategoryKind kind, LocalDate from, LocalDate to);

    List<CashVoucher> findTop5ByCompanyIdAndKindAndDeletedFalseOrderByDateDescIdDesc(Long companyId, ECategoryKind kind);
}
