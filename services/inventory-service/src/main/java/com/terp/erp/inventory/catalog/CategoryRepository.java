package com.terp.erp.inventory.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByIdAndDeletedFalse(Long id);

    List<Category> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
