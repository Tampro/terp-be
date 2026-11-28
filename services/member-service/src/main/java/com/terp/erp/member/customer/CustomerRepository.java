package com.terp.erp.member.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findFirstByCompanyIdAndKeycloakSubAndStatus(Long companyId, String keycloakSub, ECustomerStatus status);

    /** The latest membership this customer left in this shop (5-day re-join rule). */
    Optional<Customer> findFirstByCompanyIdAndKeycloakSubAndStatusOrderByLeftAtDesc(Long companyId, String keycloakSub,
                                                                                     ECustomerStatus status);
}
