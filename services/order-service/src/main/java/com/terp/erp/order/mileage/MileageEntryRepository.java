package com.terp.erp.order.mileage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MileageEntryRepository extends JpaRepository<MileageEntry, Long> {
    Page<MileageEntry> findByCompanyIdAndCustomerIdOrderByIdDesc(Long companyId, Long customerId, Pageable pageable);
}
