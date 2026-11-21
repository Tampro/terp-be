package com.terp.erp.employee.award;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;
import java.util.Optional;

public interface AwardTypeRepository extends JpaRepository<AwardType, Long> {
    /** keyword matches name or description; the caller escapes % and _ with !. */
    @Query("select t from AwardType t where t.companyId = :companyId and t.deleted = false "
            + "and (:status is null or t.status = :status) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.description) like :kw escape '!')")
    Page<AwardType> search(Long companyId, EOrgStatus status, String kw, Pageable pageable);

    List<AwardType> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EOrgStatus status);

    Optional<AwardType> findByIdAndDeletedFalse(Long id);

    @Query("select count(t) > 0 from AwardType t where t.companyId = :companyId and t.deleted = false "
            + "and lower(t.name) = lower(:name) and t.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
