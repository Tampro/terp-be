package com.terp.erp.attendance.regularization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RegularizationRepository extends JpaRepository<Regularization, Long> {
    /**
     * employeeIds = the employees whose name matched the keyword (null = no keyword on names); kw also matches the
     * reason. The caller escapes % and _ with !.
     */
    String FILTERS = "from Regularization g where g.companyId = :companyId "
            + "and (:employeeId is null or g.employeeId = :employeeId) "
            + "and (:from is null or g.workDate >= :from) and (:to is null or g.workDate <= :to) "
            + "and (:kw = '' or lower(g.reason) like :kw escape '!' or g.employeeId in :employeeIds)";

    @Query("select g " + FILTERS + " and (:status is null or g.status = :status)")
    Page<Regularization> search(Long companyId, Long employeeId, LocalDate from, LocalDate to, String kw,
                                Collection<Long> employeeIds, ERegularizationStatus status, Pageable pageable);

    /** [status, count] of the status tabs under the same filters. */
    @Query("select g.status, count(g) " + FILTERS + " group by g.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, LocalDate from, LocalDate to, String kw,
                                 Collection<Long> employeeIds);

    /** [status, count] over all time: the summary cards. */
    @Query("select g.status, count(g) from Regularization g where g.companyId = :companyId group by g.status")
    List<Object[]> summary(Long companyId);

    boolean existsByAttendanceRecordIdAndStatusAndIdNot(Long attendanceRecordId, ERegularizationStatus status, Long id);
}
