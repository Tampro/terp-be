package com.terp.erp.crm.record;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.crm.record.CrmEnums.EEntryType;
import com.terp.erp.crm.record.CrmEnums.ESubject;

import java.util.List;
import java.util.Optional;

public interface CrmEntryRepository extends JpaRepository<CrmEntry, Long> {
    List<CrmEntry> findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByIdDesc(ESubject type, Long subjectId);

    List<CrmEntry> findBySubjectTypeAndSubjectIdAndEntryTypeAndDeletedFalseOrderByIdAsc(ESubject type, Long subjectId,
                                                                                         EEntryType entryType);

    Optional<CrmEntry> findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(Long id, ESubject type, Long subjectId);
}
