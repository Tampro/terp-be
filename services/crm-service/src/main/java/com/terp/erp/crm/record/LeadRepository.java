package com.terp.erp.crm.record;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {
    Optional<Lead> findByIdAndDeletedFalse(Long id);

    @Query("""
            select l from Lead l where l.companyId = :companyId and l.deleted = false
              and (:pipelineId is null or l.pipelineId = :pipelineId)
              and (:stageId is null or l.stageId = :stageId)
              and (:ownerUserId is null or l.ownerUserId = :ownerUserId)
              and (:followFrom is null or l.followUpDate >= :followFrom)
              and (:followTo is null or l.followUpDate <= :followTo)
              and (:kw is null or lower(l.name) like :kw or lower(l.email) like :kw or lower(l.subject) like :kw)""")
    Page<Lead> search(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                      @Param("stageId") Long stageId, @Param("ownerUserId") Long ownerUserId,
                      @Param("followFrom") LocalDate followFrom, @Param("followTo") LocalDate followTo,
                      @Param("kw") String keyword, Pageable pageable);

    List<Lead> findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(Long companyId, Long pipelineId);

    boolean existsByStageIdAndDeletedFalse(Long stageId);

    boolean existsByPipelineIdAndDeletedFalse(Long pipelineId);

    @Query("select coalesce(max(l.position), 0) from Lead l where l.stageId = :stageId and l.deleted = false")
    int maxPosition(@Param("stageId") Long stageId);

    // ---------- reports ----------

    @Query("select l.stageId, count(l) from Lead l where l.companyId = :companyId and l.pipelineId = :pipelineId "
            + "and l.deleted = false group by l.stageId")
    List<Object[]> countByStage(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId);

    @Query("select l.ownerUserId, count(l), sum(case when l.convertedDealId is null then 0 else 1 end) from Lead l "
            + "where l.companyId = :companyId and (:pipelineId is null or l.pipelineId = :pipelineId) "
            + "and l.deleted = false and l.createdDate >= :from and l.createdDate < :to group by l.ownerUserId")
    List<Object[]> countByOwner(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                                @Param("from") Instant from, @Param("to") Instant to);

    @Query("select extract(month from l.createdDate), count(l), sum(case when l.convertedDealId is null then 0 else 1 end) "
            + "from Lead l where l.companyId = :companyId and (:pipelineId is null or l.pipelineId = :pipelineId) "
            + "and l.deleted = false and l.createdDate >= :from and l.createdDate < :to "
            + "group by extract(month from l.createdDate)")
    List<Object[]> countByMonth(@Param("companyId") Long companyId, @Param("pipelineId") Long pipelineId,
                                @Param("from") Instant from, @Param("to") Instant to);
}
