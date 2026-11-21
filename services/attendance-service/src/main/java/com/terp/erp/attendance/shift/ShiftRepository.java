package com.terp.erp.attendance.shift;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.attendance.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    String FILTERS = "from Shift s where s.companyId = :companyId and s.deleted = false "
            + "and (:nightShift is null or s.nightShift = :nightShift) "
            + "and (:kw = '' or lower(s.name) like :kw escape '!' or lower(s.description) like :kw escape '!')";

    /** keyword matches name or description; the caller escapes % and _ with !. nightShift = Shift Type filter. */
    @Query("select s " + FILTERS + " and (:status is null or s.status = :status)")
    Page<Shift> search(Long companyId, Boolean nightShift, EStatus status, String kw, Pageable pageable);

    /** [status, count] of the status tabs under the same filters. */
    @Query("select s.status, count(s) " + FILTERS + " group by s.status")
    List<Object[]> countByStatus(Long companyId, Boolean nightShift, String kw);

    /** [total, active, night] of the summary cards; day = total - night. */
    @Query("select count(s), coalesce(sum(case when s.status = com.terp.erp.attendance.common.EStatus.ACTIVE then 1 "
            + "else 0 end), 0), coalesce(sum(case when s.nightShift = true then 1 else 0 end), 0) "
            + "from Shift s where s.companyId = :companyId and s.deleted = false")
    List<Object[]> summary(Long companyId);

    List<Shift> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    Optional<Shift> findByIdAndDeletedFalse(Long id);

    @Query("select count(s) > 0 from Shift s where s.companyId = :companyId and s.deleted = false "
            + "and lower(s.name) = lower(:name) and s.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
