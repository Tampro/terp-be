package com.terp.erp.inventory.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxRepository extends JpaRepository<Tax, Long> {
    Optional<Tax> findByIdAndDeletedFalse(Long id);

    List<Tax> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
}
