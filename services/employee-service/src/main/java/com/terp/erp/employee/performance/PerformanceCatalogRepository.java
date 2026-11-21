package com.terp.erp.employee.performance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;
import java.util.Optional;

public interface PerformanceCatalogRepository extends JpaRepository<PerformanceCatalog, Long> {
    /** keyword on the name or description; the caller escapes with !. */
    String FILTERS = "from PerformanceCatalog c where c.companyId = :companyId and c.kind = :kind "
            + "and c.deleted = false and (:kw = '' or lower(c.name) like :kw escape '!' "
            + "or lower(c.description) like :kw escape '!')";

    @Query(value = "select c " + FILTERS + " and (:status is null or c.status = :status)",
            countQuery = "select count(c) " + FILTERS + " and (:status is null or c.status = :status)")
    Page<PerformanceCatalog> search(Long companyId, EPerformanceCatalogKind kind, EOrgStatus status, String kw,
                                    Pageable pageable);

    @Query("select c.status, count(c) " + FILTERS + " group by c.status")
    List<Object[]> countByStatus(Long companyId, EPerformanceCatalogKind kind, String kw);

    List<PerformanceCatalog> findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(
            Long companyId, EPerformanceCatalogKind kind, EOrgStatus status);

    Optional<PerformanceCatalog> findByIdAndKindAndDeletedFalse(Long id, EPerformanceCatalogKind kind);

    @Query("select count(c) > 0 from PerformanceCatalog c where c.companyId = :companyId and c.kind = :kind "
            + "and c.deleted = false and lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameTaken(Long companyId, EPerformanceCatalogKind kind, String name, Long id);
}
