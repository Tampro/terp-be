package com.terp.erp.crm.record;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;

import java.util.Collection;
import java.util.List;

public interface CrmLinkRepository extends JpaRepository<CrmLink, Long> {
    List<CrmLink> findBySubjectTypeAndSubjectIdOrderByIdAsc(ESubject subjectType, Long subjectId);

    List<CrmLink> findBySubjectTypeAndSubjectIdInOrderByIdAsc(ESubject subjectType, Collection<Long> subjectIds);

    @Modifying
    @Query("delete from CrmLink l where l.subjectType = :type and l.subjectId = :id and l.linkType = :linkType")
    void deleteLinks(@Param("type") ESubject type, @Param("id") Long id, @Param("linkType") ELinkType linkType);

    boolean existsByLinkTypeAndRefId(ELinkType linkType, Long refId);

    @Query("select l.refId, count(l) from CrmLink l where l.companyId = :companyId and l.subjectType = :type "
            + "and l.linkType = :linkType group by l.refId")
    List<Object[]> countByRef(@Param("companyId") Long companyId, @Param("type") ESubject type,
                              @Param("linkType") ELinkType linkType);
}
