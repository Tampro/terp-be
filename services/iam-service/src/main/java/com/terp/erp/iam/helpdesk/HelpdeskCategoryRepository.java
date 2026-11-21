package com.terp.erp.iam.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HelpdeskCategoryRepository extends JpaRepository<HelpdeskCategory, Long> {
    List<HelpdeskCategory> findByDeletedFalseOrderByNameAsc();

    List<HelpdeskCategory> findByActiveTrueAndDeletedFalseOrderByNameAsc();

    Optional<HelpdeskCategory> findByIdAndDeletedFalse(Long id);

    List<HelpdeskCategory> findByIdIn(Collection<Long> ids);
}
