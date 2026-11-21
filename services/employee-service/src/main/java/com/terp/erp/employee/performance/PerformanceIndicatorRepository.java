package com.terp.erp.employee.performance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;
import java.util.Optional;

public interface PerformanceIndicatorRepository extends JpaRepository<PerformanceIndicator, Long> {
    /** keyword on the name or description; the caller escapes with !. */
    String FILTERS = "from PerformanceIndicator i where i.companyId = :companyId and i.deleted = false "
            + "and (:categoryId is null or i.categoryId = :categoryId) "
            + "and (:kw = '' or lower(i.name) like :kw escape '!' or lower(i.description) like :kw escape '!')";

    @Query(value = "select i " + FILTERS + " and (:status is null or i.status = :status)",
            countQuery = "select count(i) " + FILTERS + " and (:status is null or i.status = :status)")
    Page<PerformanceIndicator> search(Long companyId, Long categoryId, EOrgStatus status, String kw,
                                      Pageable pageable);

    @Query("select i.status, count(i) " + FILTERS + " group by i.status")
    List<Object[]> countByStatus(Long companyId, Long categoryId, String kw);

    Optional<PerformanceIndicator> findByIdAndDeletedFalse(Long id);

    /** What a review rates: the company's active indicators. */
    List<PerformanceIndicator> findByCompanyIdAndStatusAndDeletedFalse(Long companyId, EOrgStatus status);

    @Query("select count(i) > 0 from PerformanceIndicator i where i.companyId = :companyId and i.deleted = false "
            + "and lower(i.name) = lower(:name) and i.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);
}
