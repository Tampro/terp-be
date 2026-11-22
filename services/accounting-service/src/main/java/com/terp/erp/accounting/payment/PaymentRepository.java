package com.terp.erp.accounting.payment;

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

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdAndDeletedFalse(Long id);

    /** Held until the transaction ends, so a payment is cleared or cancelled once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.deleted = false")
    Optional<Payment> lockById(@Param("id") Long id);

    @Query("select p from Payment p where p.companyId = :companyId and p.kind = :kind and p.deleted = false "
            + "and (:partyId is null or p.partyId = :partyId) and (:status is null or p.status = :status) "
            + "and (:bankAccountId is null or p.bankAccountId = :bankAccountId) "
            + "and (:from is null or p.paymentDate >= :from) and (:to is null or p.paymentDate <= :to)")
    Page<Payment> search(@Param("companyId") Long companyId, @Param("kind") EPaymentKind kind,
                         @Param("partyId") Long partyId, @Param("status") EPaymentStatus status,
                         @Param("bankAccountId") Long bankAccountId, @Param("from") LocalDate from,
                         @Param("to") LocalDate to, Pageable pageable);

    /** Cleared payments of a party dated in [from, to], for the customer / vendor report. */
    @Query("select p from Payment p where p.companyId = :companyId and p.kind = :kind and p.deleted = false "
            + "and p.partyId = :partyId and p.status = com.terp.erp.accounting.payment.EPaymentStatus.CLEARED "
            + "and p.paymentDate between :from and :to order by p.paymentDate, p.id")
    List<Payment> clearedBetween(@Param("companyId") Long companyId, @Param("kind") EPaymentKind kind,
                                 @Param("partyId") Long partyId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
