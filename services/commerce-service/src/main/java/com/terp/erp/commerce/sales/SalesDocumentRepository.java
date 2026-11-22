package com.terp.erp.commerce.sales;

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

public interface SalesDocumentRepository extends JpaRepository<SalesDocument, Long> {
    Optional<SalesDocument> findByIdAndDeletedFalse(Long id);

    /** Payments: the invoice row locked until the transaction ends, so two payments cannot both pass the balance check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SalesDocument d where d.id = :id and d.deleted = false")
    Optional<SalesDocument> lockById(@Param("id") Long id);

    String FILTERS = "from SalesDocument d where d.companyId = :companyId and d.kind = :kind and d.deleted = false "
            + "and (:customerId is null or d.customerId = :customerId) "
            + "and (:warehouseId is null or d.warehouseId = :warehouseId) "
            + "and (:from is null or d.docDate >= :from) and (:to is null or d.docDate <= :to) "
            + "and (:kw is null or lower(d.number) like :kw or lower(d.customerName) like :kw)";

    /** overdue = invoices posted or partly paid whose due date has passed. */
    @Query("select d " + FILTERS + " and (:status is null or d.status = :status) "
            + "and (:overdue = false or (d.status in (com.terp.erp.commerce.sales.EDocumentStatus.POSTED, "
            + "com.terp.erp.commerce.sales.EDocumentStatus.PARTIAL) and d.dueDate < :today))")
    Page<SalesDocument> search(@Param("companyId") Long companyId, @Param("kind") EDocumentKind kind,
                               @Param("customerId") Long customerId, @Param("warehouseId") Long warehouseId,
                               @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("kw") String keyword,
                               @Param("status") EDocumentStatus status, @Param("overdue") boolean overdue,
                               @Param("today") LocalDate today, Pageable pageable);

    @Query("select d.status, count(d) " + FILTERS + " group by d.status")
    List<Object[]> countByStatus(@Param("companyId") Long companyId, @Param("kind") EDocumentKind kind,
                                 @Param("customerId") Long customerId, @Param("warehouseId") Long warehouseId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("kw") String keyword);

    /** Unified calendar: documents of a kind falling due in [from, to]. */
    @Query("select d from SalesDocument d where d.companyId = :companyId and d.kind = :kind and d.deleted = false "
            + "and d.dueDate between :from and :to order by d.dueDate, d.id")
    List<SalesDocument> dueBetween(@Param("companyId") Long companyId, @Param("kind") EDocumentKind kind,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Highest version number in a quotation family (the first version and every version made of it). */
    @Query("select max(d.versionNo) from SalesDocument d where d.id = :rootId or d.versionOfId = :rootId")
    Integer maxVersion(@Param("rootId") Long rootId);
}
