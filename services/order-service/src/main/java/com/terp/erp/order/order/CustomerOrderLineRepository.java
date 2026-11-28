package com.terp.erp.order.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderLineRepository extends JpaRepository<CustomerOrderLine, Long> {
    List<CustomerOrderLine> findByOrderIdOrderByIdAsc(Long orderId);
}
