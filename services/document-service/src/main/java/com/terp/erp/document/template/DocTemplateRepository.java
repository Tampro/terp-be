package com.terp.erp.document.template;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.document.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface DocTemplateRepository extends JpaRepository<DocTemplate, Long> {
    /** keyword on name and description; groupId = contract type (CONTRACT) or category (DOCUMENT). */
    String FILTERS = "from DocTemplate t where t.companyId = :companyId and t.kind = :kind and t.deleted = false "
            + "and (:groupId is null or t.contractTypeId = :groupId or t.categoryId = :groupId) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.description) like :kw escape '!')";

    @Query(value = "select t " + FILTERS + " and (:status is null or t.status = :status)",
            countQuery = "select count(t) " + FILTERS + " and (:status is null or t.status = :status)")
    Page<DocTemplate> search(Long companyId, DocTemplate.EKind kind, Long groupId, EStatus status, String kw,
                             Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, DocTemplate.EKind kind, Long groupId, String kw);

    /** Set as Default: the others of the same group lose it. */
    @Modifying(flushAutomatically = true)
    @Query("update DocTemplate t set t.defaultTemplate = false where t.companyId = :companyId and t.kind = :kind "
            + "and t.id <> :id and ((:kind = com.terp.erp.document.template.DocTemplate.EKind.CONTRACT "
            + "and t.contractTypeId = :groupId) or (:kind = com.terp.erp.document.template.DocTemplate.EKind.DOCUMENT "
            + "and t.categoryId = :groupId))")
    int clearDefault(Long companyId, DocTemplate.EKind kind, Long groupId, Long id);

    boolean existsByContractTypeIdAndDeletedFalse(Long contractTypeId);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    Optional<DocTemplate> findByIdAndKindAndDeletedFalse(Long id, DocTemplate.EKind kind);
}
