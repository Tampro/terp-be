package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;
import java.util.Optional;

public interface LifecycleCatalogRepository extends JpaRepository<LifecycleCatalog, Long> {
    @Query("select c from LifecycleCatalog c where c.companyId = :companyId and c.kind = :kind and c.deleted = false "
            + "and (:status is null or c.status = :status) and (:kw = '' or lower(c.name) like :kw escape '!')")
    Page<LifecycleCatalog> search(Long companyId, ELifecycleCatalogKind kind, EOrgStatus status, String kw,
                                  Pageable pageable);

    List<LifecycleCatalog> findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(Long companyId,
                                                                                      ELifecycleCatalogKind kind,
                                                                                      EOrgStatus status);

    Optional<LifecycleCatalog> findByIdAndKindAndDeletedFalse(Long id, ELifecycleCatalogKind kind);

    @Query("select count(c) > 0 from LifecycleCatalog c where c.companyId = :companyId and c.kind = :kind "
            + "and c.deleted = false and lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameTaken(Long companyId, ELifecycleCatalogKind kind, String name, Long id);
}
