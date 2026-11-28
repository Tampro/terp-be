package com.terp.erp.order.ret;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {
    List<OrderReturn> findByOrderIdOrderByIdDesc(Long orderId);

    Optional<OrderReturn> findFirstByOrderIdAndStatusIn(Long orderId, Collection<EReturnStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReturn r where r.id = :id")
    Optional<OrderReturn> lockById(@Param("id") Long id);

    @Query("select r from OrderReturn r where r.companyId = :company and (:status is null or r.status = :status) "
            + "and (:storeId is null or r.storeId = :storeId) order by r.id desc")
    Page<OrderReturn> search(@Param("company") Long companyId, @Param("status") EReturnStatus status,
                             @Param("storeId") Long storeId, Pageable pageable);
}
