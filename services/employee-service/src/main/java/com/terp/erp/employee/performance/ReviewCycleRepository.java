package com.terp.erp.employee.performance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;
import java.util.Optional;

public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, Long> {
    /** keyword on the name or description; the caller escapes with !. */
    @Query("select c from ReviewCycle c where c.companyId = :companyId and c.deleted = false "
            + "and (:frequency is null or c.frequency = :frequency) and (:status is null or c.status = :status) "
            + "and (:kw = '' or lower(c.name) like :kw escape '!' or lower(c.description) like :kw escape '!')")
    Page<ReviewCycle> search(Long companyId, ReviewCycle.EFrequency frequency, EOrgStatus status, String kw,
                             Pageable pageable);

    List<ReviewCycle> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EOrgStatus status);

    Optional<ReviewCycle> findByIdAndDeletedFalse(Long id);

    @Query("select count(c) > 0 from ReviewCycle c where c.companyId = :companyId and c.deleted = false "
            + "and lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
