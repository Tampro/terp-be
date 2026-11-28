package com.terp.erp.order.mileage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MileageUseLotRepository extends JpaRepository<MileageUseLot, MileageUseLot.Key> {
    List<MileageUseLot> findByOrderId(Long orderId);
}
