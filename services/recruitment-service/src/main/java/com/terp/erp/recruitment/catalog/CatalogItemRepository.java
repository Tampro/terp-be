package com.terp.erp.recruitment.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.recruitment.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
    /** keyword matches name or description; the caller escapes % and _ with !. */
    @Query("select c from CatalogItem c where c.companyId = :companyId and c.kind = :kind and c.deleted = false "
            + "and (:status is null or c.status = :status) "
            + "and (:kw = '' or lower(c.name) like :kw escape '!' or lower(c.description) like :kw escape '!')")
    Page<CatalogItem> search(Long companyId, ECatalogKind kind, EStatus status, String kw, Pageable pageable);

    List<CatalogItem> findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, ECatalogKind kind,
                                                                                  EStatus status);

    Optional<CatalogItem> findByIdAndKindAndDeletedFalse(Long id, ECatalogKind kind);

    @Query("select count(c) > 0 from CatalogItem c where c.companyId = :companyId and c.kind = :kind "
            + "and c.deleted = false and lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameTaken(Long companyId, ECatalogKind kind, String name, Long id);
}
