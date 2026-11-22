package com.terp.erp.commerce.purchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {
    Optional<PurchaseInvoice> findByIdAndDeletedFalse(Long id);

    /** Payments: the invoice row locked until the transaction ends, so two payments cannot both pass the balance check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PurchaseInvoice d where d.id = :id and d.deleted = false")
    Optional<PurchaseInvoice> lockById(@Param("id") Long id);

    /** Unified calendar: invoices falling due in [from, to]. */
    @Query("select d from PurchaseInvoice d where d.companyId = :companyId and d.deleted = false "
            + "and d.dueDate between :from and :to order by d.dueDate, d.id")
    java.util.List<PurchaseInvoice> dueBetween(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDate from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDate to);

    String FILTERS = "from PurchaseInvoice d where d.companyId = :companyId and d.deleted = false "
            + "and (:vendorId is null or d.vendorId = :vendorId) "
            + "and (:warehouseId is null or d.warehouseId = :warehouseId) "
            + "and (:from is null or d.invoiceDate >= :from) and (:to is null or d.invoiceDate <= :to) "
            + "and (:kw is null or lower(d.number) like :kw or lower(d.vendorName) like :kw)";

    /** overdue = posted or partly paid past the due date. */
    @Query("select d " + FILTERS + " and (:status is null or d.status = :status) "
            + "and (:overdue = false or (d.status in (com.terp.erp.commerce.purchase.PurchaseInvoice.EStatus.POSTED, "
            + "com.terp.erp.commerce.purchase.PurchaseInvoice.EStatus.PARTIAL) and d.dueDate < :today))")
    Page<PurchaseInvoice> search(@Param("companyId") Long companyId, @Param("vendorId") Long vendorId,
                                 @Param("warehouseId") Long warehouseId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to, @Param("kw") String keyword,
                                 @Param("status") PurchaseInvoice.EStatus status, @Param("overdue") boolean overdue,
                                 @Param("today") LocalDate today, Pageable pageable);

    @Query("select d.status, count(d) " + FILTERS + " group by d.status")
    List<Object[]> countByStatus(@Param("companyId") Long companyId, @Param("vendorId") Long vendorId,
                                 @Param("warehouseId") Long warehouseId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to, @Param("kw") String keyword);
}
