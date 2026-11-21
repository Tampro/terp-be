package com.terp.erp.inventory.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    Optional<Unit> findByIdAndDeletedFalse(Long id);

    List<Unit> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
}
