package com.terp.erp.member.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    /** Default first, then newest. */
    @Query("select a from CustomerAddress a where a.customerId = :customerId order by a.isDefault desc, a.id desc")
    List<CustomerAddress> ofCustomer(Long customerId);

    long countByCustomerId(Long customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);

    @Modifying
    @Query("delete from CustomerAddress a where a.customerId = :customerId")
    int deleteOfCustomer(Long customerId);
}
