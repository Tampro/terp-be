package com.terp.erp.crm.record;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.crm.record.CrmEnums.ESubject;

import java.util.List;
import java.util.Optional;

public interface CrmCallRepository extends JpaRepository<CrmCall, Long> {
    List<CrmCall> findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByIdDesc(ESubject type, Long subjectId);

    Optional<CrmCall> findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(Long id, ESubject type, Long subjectId);
}
