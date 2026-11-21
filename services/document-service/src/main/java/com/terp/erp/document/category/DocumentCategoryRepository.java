package com.terp.erp.document.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.document.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
    /** keyword on name and description; the caller escapes with !. */
    @Query("select c from DocumentCategory c where c.companyId = :companyId and c.deleted = false "
            + "and (:status is null or c.status = :status) and (:mandatory is null or c.mandatory = :mandatory) "
            + "and (:kw = '' or lower(c.name) like :kw escape '!' or lower(c.description) like :kw escape '!')")
    Page<DocumentCategory> search(Long companyId, EStatus status, Boolean mandatory, String kw, Pageable pageable);

    Optional<DocumentCategory> findByIdAndDeletedFalse(Long id);

    List<DocumentCategory> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
