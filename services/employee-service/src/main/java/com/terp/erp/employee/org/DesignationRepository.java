package com.terp.erp.employee.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DesignationRepository extends JpaRepository<Designation, Long> {
    /** keyword matches name or description; the caller escapes % and _ with ! (a keyword is text, not a pattern). */
    @org.springframework.data.jpa.repository.Query("select d from Designation d where d.companyId = :companyId "
            + "and d.deleted = false and (:departmentId is null or d.departmentId = :departmentId) "
            + "and (:status is null or d.status = :status) "
            + "and (:kw = '' or lower(d.name) like :kw escape '!' or lower(d.description) like :kw escape '!')")
    org.springframework.data.domain.Page<Designation> search(Long companyId, Long departmentId, EOrgStatus status,
                                                             String kw, org.springframework.data.domain.Pageable pageable);

    List<Designation> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EOrgStatus status);

    List<Designation> findByCompanyIdAndDepartmentIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId,
                                                                                         Long departmentId,
                                                                                         EOrgStatus status);

    Optional<Designation> findByIdAndDeletedFalse(Long id);

    List<Designation> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    List<Designation> findByCompanyIdAndDepartmentIdAndDeletedFalseOrderByNameAsc(Long companyId, Long departmentId);

    List<Designation> findByCompanyIdAndIdInAndDeletedFalse(Long companyId, Collection<Long> ids);

    boolean existsByDepartmentIdAndNameAndDeletedFalseAndIdNot(Long departmentId, String name, Long id);

    boolean existsByDepartmentIdAndDeletedFalse(Long departmentId);

    long countByDepartmentIdAndDeletedFalse(Long departmentId);
}
