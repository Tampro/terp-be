package com.terp.erp.commerce.salesreturn;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    Optional<SalesReturn> findByIdAndDeletedFalse(Long id);

    @Query("""
            select r from SalesReturn r where r.companyId = :companyId and r.deleted = false
              and (:invoiceId is null or r.invoiceId = :invoiceId)
              and (:customerId is null or r.customerId = :customerId)
              and (:warehouseId is null or r.warehouseId = :warehouseId)
              and (:status is null or r.status = :status)
              and (:from is null or r.returnDate >= :from) and (:to is null or r.returnDate <= :to)
              and (:kw is null or lower(r.number) like :kw or lower(r.customerName) like :kw)""")
    Page<SalesReturn> search(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
                             @Param("customerId") Long customerId, @Param("warehouseId") Long warehouseId,
                             @Param("status") SalesReturn.EStatus status, @Param("from") LocalDate from,
                             @Param("to") LocalDate to, @Param("kw") String keyword, Pageable pageable);

    /** Quantity already returned per invoice line, every non-deleted return counted (draft included). */
    @Query("""
            select l.invoiceLineId, sum(l.quantity) from SalesReturn r join r.lines l
            where r.deleted = false and l.invoiceLineId in :lineIds and (:excludeId is null or r.id <> :excludeId)
            group by l.invoiceLineId""")
    List<Object[]> returnedByLine(@Param("lineIds") Collection<Long> lineIds, @Param("excludeId") Long excludeId);

    default java.util.Map<Long, BigDecimal> returned(Collection<Long> lineIds, Long excludeId) {
        java.util.Map<Long, BigDecimal> m = new java.util.HashMap<>();
        for (Object[] r : returnedByLine(lineIds, excludeId)) {
            m.put((Long) r[0], (BigDecimal) r[1]);
        }
        return m;
    }
}
