package com.terp.erp.attendance.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.attendance.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {
    String FILTERS = "from LeavePolicy p where p.companyId = :companyId and p.deleted = false "
            + "and (:leaveTypeId is null or p.leaveTypeId = :leaveTypeId) "
            + "and (:kw = '' or lower(p.name) like :kw escape '!' or lower(p.description) like :kw escape '!')";

    @Query("select p " + FILTERS + " and (:status is null or p.status = :status)")
    Page<LeavePolicy> search(Long companyId, Long leaveTypeId, EStatus status, String kw, Pageable pageable);

    /** [status, count] of the status tabs under the same filters. */
    @Query("select p.status, count(p) " + FILTERS + " group by p.status")
    List<Object[]> countByStatus(Long companyId, Long leaveTypeId, String kw);

    Optional<LeavePolicy> findByIdAndDeletedFalse(Long id);

    /** The active policy of a leave type, if any (at most one, ATT_036). */
    Optional<LeavePolicy> findFirstByLeaveTypeIdAndStatusAndDeletedFalse(Long leaveTypeId, EStatus status);

    boolean existsByLeaveTypeIdAndStatusAndDeletedFalseAndIdNot(Long leaveTypeId, EStatus status, Long id);

    boolean existsByLeaveTypeIdAndDeletedFalse(Long leaveTypeId);

    @Query("select count(p) > 0 from LeavePolicy p where p.companyId = :companyId and p.deleted = false "
            + "and lower(p.name) = lower(:name) and p.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
