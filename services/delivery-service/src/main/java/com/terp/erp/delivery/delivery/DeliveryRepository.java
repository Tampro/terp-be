package com.terp.erp.delivery.delivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Optional<Delivery> findByCompanyIdAndOrderId(Long companyId, Long orderId);

    Optional<Delivery> findByIdAndCompanyIdAndRiderSub(Long id, Long companyId, String riderSub);

    @Query("""
            select d from Delivery d where d.companyId = :company
              and (:storeId is null or d.storeId = :storeId)
              and (:status is null or d.status = :status)
              and (:riderSub is null or d.riderSub = :riderSub)""")
    Page<Delivery> search(@Param("company") Long companyId, @Param("storeId") Long storeId,
                          @Param("status") EDeliveryStatus status, @Param("riderSub") String riderSub, Pageable pageable);
}
