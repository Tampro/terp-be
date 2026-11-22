package com.terp.erp.accounting.goal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface GoalCategoryRepository extends JpaRepository<GoalCategory, Long> {
    Optional<GoalCategory> findByIdAndDeletedFalse(Long id);

    @Query("select c from GoalCategory c where c.companyId = :companyId and c.deleted = false "
            + "and (:active is null or c.active = :active)")
    Page<GoalCategory> search(Long companyId, Boolean active, Pageable pageable);

    /** id = -1 when creating. */
    boolean existsByCompanyIdAndCodeAndDeletedFalseAndIdNot(Long companyId, String code, Long id);
}
