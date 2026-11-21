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

    /** [subjectId, total, done] for the Tasks column of a list page, one query. */
    @Query("select t.subjectId, count(t), sum(case when t.status = com.terp.erp.crm.record.CrmEnums.ETaskStatus.DONE "
            + "then 1 else 0 end) from CrmTask t where t.subjectType = :type and t.subjectId in :ids "
            + "and t.deleted = false group by t.subjectId")
    List<Object[]> progress(@Param("type") ESubject type, @Param("ids") Collection<Long> ids);
}
