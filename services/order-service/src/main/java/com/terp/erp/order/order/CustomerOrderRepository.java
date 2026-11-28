package com.terp.erp.order.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByCompanyIdAndOrderNoAndCustomerId(Long companyId, String orderNo, Long customerId);

    Optional<CustomerOrder> findFirstByCustomerIdAndStoreIdAndStatus(Long customerId, Long storeId, EOrderStatus status);

    Page<CustomerOrder> findByCustomerIdAndCompanyIdOrderByIdDesc(Long customerId, Long companyId, Pageable pageable);

    /** Back Office list: null filters are ignored. */
    @Query("select o from CustomerOrder o where o.companyId = :companyId "
            + "and (:storeId is null or o.storeId = :storeId) and (:status is null or o.status = :status) "
            + "and (:from is null or o.createdDate >= :from) and (:to is null or o.createdDate < :to)")
    Page<CustomerOrder> search(Long companyId, Long storeId, EOrderStatus status, Instant from, Instant to, Pageable pageable);

    /** PENDING_PAYMENT orders created before {@code before} become EXPIRED. */
    @Query("select o.id from CustomerOrder o where o.status = com.terp.erp.order.order.EOrderStatus.PENDING_PAYMENT "
            + "and o.createdDate < :before order by o.id")
    java.util.List<Long> idsToExpire(Instant before);

    @Modifying
    @Query("update CustomerOrder o set o.status = com.terp.erp.order.order.EOrderStatus.EXPIRED "
            + "where o.status = com.terp.erp.order.order.EOrderStatus.PENDING_PAYMENT and o.createdDate < :before")
    int expire(Instant before);
}
