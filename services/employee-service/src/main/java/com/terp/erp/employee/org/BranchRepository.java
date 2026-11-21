package com.terp.erp.employee.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    /** keyword matches name, email or phone; a keyword is text, not a pattern (% and _ escaped by the caller). */
    @org.springframework.data.jpa.repository.Query("select b from Branch b where b.companyId = :companyId and b.deleted = false "
            + "and (:status is null or b.status = :status) and (:kw = '' or lower(b.name) like :kw escape '\\' "
            + "or lower(b.email) like :kw escape '\\' or b.phone like :kw escape '\\')")
    org.springframework.data.domain.Page<Branch> search(Long companyId, EOrgStatus status, String kw,
                                                         org.springframework.data.domain.Pageable pageable);

    List<Branch> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EOrgStatus status);

    Optional<Branch> findByIdAndDeletedFalse(Long id);

    List<Branch> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    List<Branch> findByCompanyIdAndIdInAndDeletedFalse(Long companyId, Collection<Long> ids);

    boolean existsByCompanyIdAndNameAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
