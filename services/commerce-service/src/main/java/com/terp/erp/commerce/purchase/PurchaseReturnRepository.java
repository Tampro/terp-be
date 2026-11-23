package com.terp.erp.commerce.purchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {
    Optional<PurchaseReturn> findByIdAndDeletedFalse(Long id);

    @Query("""
            select r from PurchaseReturn r where r.companyId = :companyId and r.deleted = false
              and (:invoiceId is null or r.invoiceId = :invoiceId)
              and (:vendorId is null or r.vendorId = :vendorId)
              and (:warehouseId is null or r.warehouseId = :warehouseId)
              and (:status is null or r.status = :status)
              and (:from is null or r.returnDate >= :from) and (:to is null or r.returnDate <= :to)
              and (:kw is null or lower(r.number) like :kw or lower(r.vendorName) like :kw)
              and (:createdBy is null or r.createdBy = :createdBy)""")
    Page<PurchaseReturn> search(@Param("companyId") Long companyId, @Param("invoiceId") Long invoiceId,
                                @Param("vendorId") Long vendorId, @Param("warehouseId") Long warehouseId,
                                @Param("status") PurchaseReturn.EStatus status, @Param("from") LocalDate from,
                                @Param("to") LocalDate to, @Param("kw") String keyword,
                                @Param("createdBy") String createdBy, Pageable pageable);

    /** Vendor portal: purchase returns to one vendor, drafts excluded. */
    @Query("""
            select r from PurchaseReturn r where r.companyId = :companyId and r.deleted = false
              and r.vendorId = :vendorId and r.status <> com.terp.erp.commerce.purchase.PurchaseReturn.EStatus.DRAFT""")
    Page<PurchaseReturn> forVendor(@Param("companyId") Long companyId, @Param("vendorId") Long vendorId,
                                   Pageable pageable);

    /** Quantity already returned per invoice line, every non-deleted return counted (draft included). */
    @Query("""
            select l.invoiceLineId, sum(l.quantity) from PurchaseReturn r join r.lines l
            where r.deleted = false and l.invoiceLineId in :lineIds and (:excludeId is null or r.id <> :excludeId)
            group by l.invoiceLineId""")
    List<Object[]> returnedByLine(@Param("lineIds") Collection<Long> lineIds, @Param("excludeId") Long excludeId);

    default Map<Long, BigDecimal> returned(Collection<Long> lineIds, Long excludeId) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : returnedByLine(lineIds, excludeId)) {
            m.put((Long) r[0], (BigDecimal) r[1]);
        }
        return m;
    }
}
