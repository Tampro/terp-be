package com.terp.erp.support.kb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbCategoryRepository extends JpaRepository<KbCategory, Long> {
    Optional<KbCategory> findByIdAndDeletedFalse(Long id);

    List<KbCategory> findByCompanyIdAndDeletedFalseOrderByTitleAsc(Long companyId);
}
