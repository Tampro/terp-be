package com.terp.erp.employee.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    String SEARCH = "from Department d where d.companyId = :companyId and d.deleted = false "
            + "and (:branchId is null or d.branchId = :branchId) "
            + "and (:kw = '' or lower(d.name) like :kw escape '!' or lower(d.description) like :kw escape '!')";

    /** keyword matches name or description; the caller escapes % and _ with ! (a keyword is text, not a pattern). */
    @org.springframework.data.jpa.repository.Query("select d " + SEARCH + " and (:status is null or d.status = :status)")
    org.springframework.data.domain.Page<Department> search(Long companyId, Long branchId, EOrgStatus status, String kw,
                                                            org.springframework.data.domain.Pageable pageable);

    /** [status, count] for the status tabs under the same filters. */
    @org.springframework.data.jpa.repository.Query("select d.status, count(d) " + SEARCH + " group by d.status")
    List<Object[]> countByStatus(Long companyId, Long branchId, String kw);

    List<Department> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EOrgStatus status);

    List<Department> findByCompanyIdAndBranchIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, Long branchId,
                                                                                    EOrgStatus status);

    Optional<Department> findByIdAndDeletedFalse(Long id);

    List<Department> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    List<Department> findByCompanyIdAndBranchIdAndDeletedFalseOrderByNameAsc(Long companyId, Long branchId);

    List<Department> findByCompanyIdAndIdInAndDeletedFalse(Long companyId, Collection<Long> ids);

    boolean existsByBranchIdAndNameAndDeletedFalseAndIdNot(Long branchId, String name, Long id);

    boolean existsByBranchIdAndDeletedFalse(Long branchId);

    long countByBranchIdAndDeletedFalse(Long branchId);
}
