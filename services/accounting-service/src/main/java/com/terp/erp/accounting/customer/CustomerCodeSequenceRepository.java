package com.terp.erp.accounting.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CustomerCodeSequenceRepository extends JpaRepository<CustomerCodeSequence, Long> {
    /** Creates the company's row when missing; INSERT IGNORE so two first customers at once don't fail. */
    @Modifying
    @Query(value = "insert ignore into customer_code_sequence (company_id, last_number) values (:companyId, 0)",
            nativeQuery = true)
    void ensureRow(Long companyId);

    /** SELECT … FOR UPDATE: concurrent creates in one company wait here, so two customers never get one code. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CustomerCodeSequence s where s.companyId = :companyId")
    Optional<CustomerCodeSequence> lockByCompanyId(Long companyId);
}
