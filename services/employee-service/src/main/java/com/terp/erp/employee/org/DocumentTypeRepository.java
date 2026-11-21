package com.terp.erp.employee.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    Optional<DocumentType> findByIdAndDeletedFalse(Long id);

    /** System types plus the company's own. */
    @Query("select t from DocumentType t where t.deleted = false and (t.companyId is null or t.companyId = :companyId) "
            + "order by t.companyId asc nulls first, t.name asc")
    List<DocumentType> visibleTo(Long companyId);

    @Query("select count(t) > 0 from DocumentType t where t.deleted = false and t.name = :name and t.id <> :id "
            + "and (t.companyId is null or t.companyId = :companyId)")
    boolean nameTaken(Long companyId, String name, Long id);
}
