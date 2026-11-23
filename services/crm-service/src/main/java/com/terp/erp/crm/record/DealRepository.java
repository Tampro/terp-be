package com.terp.erp.crm.record;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {
    Optional<Deal> findByIdAndDeletedFalse(Long id);

    @Query("""
            select d from Deal d where d.companyId = :companyId and d.deleted = false
              and (:pipelineId is null or d.pipelineId = :pipelineId)
              and (:stageId is null or d.stageId = :stageId)
              and (:status is null or d.status = :status)
              and (:kw is null or lower(d.name) like :kw)""")
    Page<Deal> search(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                      @Param("stageId") Long stageId, @Param("status") EDealStatus status, @Param("kw") String keyword,
                      Pageable pageable);

    /** {@link #search} limited to deals I created or am a MEMBER of (CRM_VIEW_OWN). */
    @Query("""
            select d from Deal d where d.companyId = :companyId and d.deleted = false
              and (d.createdBy = :sub or exists (select k.id from CrmLink k where k.subjectType = :dealType
                   and k.subjectId = d.id and k.linkType = :memberType and k.refId = :me))
              and (:pipelineId is null or d.pipelineId = :pipelineId)
              and (:stageId is null or d.stageId = :stageId)
              and (:status is null or d.status = :status)
              and (:kw is null or lower(d.name) like :kw)""")
    Page<Deal> searchOwn(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                         @Param("stageId") Long stageId, @Param("status") EDealStatus status,
                         @Param("kw") String keyword, @Param("me") Long me, @Param("sub") String sub,
                         @Param("dealType") ESubject dealType, @Param("memberType") ELinkType memberType,
                         Pageable pageable);

    /** Deals whose CLIENT link is this accounting customer (client portal). */
    @Query("""
            select d from Deal d where d.companyId = :companyId and d.deleted = false
              and exists (select k.id from CrmLink k where k.subjectType = :dealType and k.subjectId = d.id
                   and k.linkType = :clientType and k.refId = :customerId)""")
    Page<Deal> findOfClient(@Param("companyId") Long companyId, @Param("customerId") Long customerId,
                            @Param("dealType") ESubject dealType, @Param("clientType") ELinkType clientType,
                            Pageable pageable);

    List<Deal> findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(Long companyId, Long pipelineId);

    boolean existsByStageIdAndDeletedFalse(Long stageId);

    boolean existsByPipelineIdAndDeletedFalse(Long pipelineId);

    @Query("select coalesce(max(d.position), 0) from Deal d where d.stageId = :stageId and d.deleted = false")
    int maxPosition(@Param("stageId") Long stageId);

    // ---------- reports ----------

    @Query("select d.stageId, count(d), coalesce(sum(d.price), 0) from Deal d where d.companyId = :companyId "
            + "and d.pipelineId = :pipelineId and d.deleted = false group by d.stageId")
    List<Object[]> countByStage(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId);

    @Query("select d.status, count(d), coalesce(sum(d.price), 0) from Deal d where d.companyId = :companyId "
            + "and (:pipelineId is null or d.pipelineId = :pipelineId) and d.deleted = false "
            + "and d.createdDate >= :from and d.createdDate < :to group by d.status")
    List<Object[]> countByStatus(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                                 @Param("from") Instant from, @Param("to") Instant to);

    @Query("select extract(month from d.createdDate), count(d), coalesce(sum(d.price), 0) from Deal d "
            + "where d.companyId = :companyId and (:pipelineId is null or d.pipelineId = :pipelineId) "
            + "and d.deleted = false and d.createdDate >= :from and d.createdDate < :to "
            + "group by extract(month from d.createdDate)")
    List<Object[]> countByMonth(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                                @Param("from") Instant from, @Param("to") Instant to);
}
