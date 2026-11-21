package com.terp.erp.document.hr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HrDocumentRepository extends JpaRepository<HrDocument, Long> {
    /** keyword on title and description; the caller escapes with !. */
    String FILTERS = "from HrDocument d where d.companyId = :companyId and d.deleted = false "
            + "and (:categoryId is null or d.categoryId = :categoryId) "
            + "and (:kw = '' or lower(d.title) like :kw escape '!' or lower(d.description) like :kw escape '!')";

    /** Past its expiry date and not archived: reads as EXPIRED. */
    String LAPSED = "(d.expiryDate is not null and d.expiryDate < :today "
            + "and d.status <> com.terp.erp.document.hr.HrDocument.EStatus.ARCHIVED)";

    /** The effective status as a JPQL expression. */
    String EFFECTIVE = "case when " + LAPSED + " then com.terp.erp.document.hr.HrDocument.EStatus.EXPIRED "
            + "else d.status end";

    @Query(value = "select d " + FILTERS + " and (:status is null or " + EFFECTIVE + " = :status)",
            countQuery = "select count(d) " + FILTERS + " and (:status is null or " + EFFECTIVE + " = :status)")
    Page<HrDocument> search(Long companyId, Long categoryId, HrDocument.EStatus status, String kw, LocalDate today,
                            Pageable pageable);

    @Query("select " + EFFECTIVE + ", count(d) " + FILTERS + " group by " + EFFECTIVE)
    List<Object[]> countByStatus(Long companyId, Long categoryId, String kw, LocalDate today);

    /** Expiring Soon: published (not lapsed) with an expiry within [today, until]. */
    @Query("select count(d) from HrDocument d where d.companyId = :companyId and d.deleted = false "
            + "and d.status = com.terp.erp.document.hr.HrDocument.EStatus.PUBLISHED "
            + "and d.expiryDate between :today and :until")
    long countExpiringSoon(Long companyId, LocalDate today, LocalDate until);

    long countByCompanyIdAndDeletedFalseAndRequiresAcknowledgmentTrue(Long companyId);

    @Query("select d.categoryId, count(d) from HrDocument d where d.deleted = false and d.categoryId in :ids "
            + "group by d.categoryId")
    List<Object[]> countByCategory(Collection<Long> ids);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    Optional<HrDocument> findByIdAndDeletedFalse(Long id);

    List<HrDocument> findByCompanyIdAndDeletedFalseOrderByTitleAsc(Long companyId);
}
