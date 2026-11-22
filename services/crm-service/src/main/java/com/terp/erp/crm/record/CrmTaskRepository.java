package com.terp.erp.crm.record;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.record.CrmEnums.ESubject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrmTaskRepository extends JpaRepository<CrmTask, Long> {
    List<CrmTask> findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByTaskDateAscIdAsc(ESubject type, Long subjectId);

    Optional<CrmTask> findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(Long id, ESubject type, Long subjectId);

    /** Unified calendar: the company's lead or deal tasks dated in [from, to]. */
    @Query("select t from CrmTask t where t.companyId = :companyId and t.subjectType = :type and t.deleted = false "
            + "and t.taskDate between :from and :to order by t.taskDate, t.taskTime, t.id")
    List<CrmTask> inPeriod(@Param("companyId") Long companyId, @Param("type") ESubject type,
                           @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    /** [subjectId, total, done] for the Tasks column of a list page, one query. */
    @Query("select t.subjectId, count(t), sum(case when t.status = com.terp.erp.crm.record.CrmEnums.ETaskStatus.DONE "
            + "then 1 else 0 end) from CrmTask t where t.subjectType = :type and t.subjectId in :ids "
            + "and t.deleted = false group by t.subjectId")
    List<Object[]> progress(@Param("type") ESubject type, @Param("ids") Collection<Long> ids);
}
