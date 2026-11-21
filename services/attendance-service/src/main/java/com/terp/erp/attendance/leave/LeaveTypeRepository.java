package com.terp.erp.attendance.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.attendance.common.EStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    /** keyword matches the name; the caller escapes % and _ with !. */
    @Query("select t from LeaveType t where t.companyId = :companyId and t.deleted = false "
            + "and (:status is null or t.status = :status) and (:kw = '' or lower(t.name) like :kw escape '!')")
    Page<LeaveType> search(Long companyId, EStatus status, String kw, Pageable pageable);

    List<LeaveType> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    List<LeaveType> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    Optional<LeaveType> findByIdAndDeletedFalse(Long id);

    @Query("select count(t) > 0 from LeaveType t where t.companyId = :companyId and t.deleted = false "
            + "and lower(t.name) = lower(:name) and t.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
