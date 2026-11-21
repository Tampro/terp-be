package com.terp.erp.attendance.policy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.attendance.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, Long> {
    String FILTERS = "from AttendancePolicy p where p.companyId = :companyId and p.deleted = false "
            + "and (:kw = '' or lower(p.name) like :kw escape '!' or lower(p.description) like :kw escape '!')";

    /** keyword matches name or description; the caller escapes % and _ with !. */
    @Query("select p " + FILTERS + " and (:status is null or p.status = :status)")
    Page<AttendancePolicy> search(Long companyId, EStatus status, String kw, Pageable pageable);

    /** [status, count] of the status tabs under the keyword. */
    @Query("select p.status, count(p) " + FILTERS + " group by p.status")
    List<Object[]> countByStatus(Long companyId, String kw);

    /** [total, active, avg late grace, avg overtime rate] of the summary cards, over every policy of the company. */
    @Query("select count(p), coalesce(sum(case when p.status = com.terp.erp.attendance.common.EStatus.ACTIVE "
            + "then 1 else 0 end), 0), avg(p.lateArrivalGraceMinutes), avg(p.overtimeRatePerHour) "
            + "from AttendancePolicy p where p.companyId = :companyId and p.deleted = false")
    List<Object[]> summary(Long companyId);

    List<AttendancePolicy> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    Optional<AttendancePolicy> findByIdAndDeletedFalse(Long id);

    @Query("select count(p) > 0 from AttendancePolicy p where p.companyId = :companyId and p.deleted = false "
            + "and lower(p.name) = lower(:name) and p.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
