package com.terp.erp.meeting.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyEventRepository extends JpaRepository<CompanyEvent, Long> {
    /** keyword on title, location and description; the caller escapes with !. */
    String FILTERS = "from CompanyEvent e where e.companyId = :companyId and e.deleted = false "
            + "and (:eventTypeId is null or e.eventTypeId = :eventTypeId) "
            + "and (:departmentId is null or exists (select 1 from CompanyEvent x join x.departments d "
            + "where x.id = e.id and d.departmentId = :departmentId)) "
            + "and (:kw = '' or lower(e.title) like :kw escape '!' or lower(e.location) like :kw escape '!' "
            + "or lower(e.description) like :kw escape '!')";

    @Query(value = "select e " + FILTERS + " and (:status is null or e.status = :status)",
            countQuery = "select count(e) " + FILTERS + " and (:status is null or e.status = :status)")
    Page<CompanyEvent> search(Long companyId, Long eventTypeId, Long departmentId, CompanyEvent.EStatus status,
                              String kw, Pageable pageable);

    @Query("select e.status, count(e) " + FILTERS + " group by e.status")
    List<Object[]> countByStatus(Long companyId, Long eventTypeId, Long departmentId, String kw);

    /** Approved events overlapping [from, to] (the calendar). */
    @Query("select e from CompanyEvent e where e.companyId = :companyId and e.deleted = false "
            + "and e.status = com.terp.erp.meeting.event.CompanyEvent.EStatus.APPROVED "
            + "and e.startDate <= :to and e.endDate >= :from order by e.startDate, e.startTime, e.id")
    List<CompanyEvent> approvedBetween(Long companyId, LocalDate from, LocalDate to);

    @Query("select e.eventTypeId, count(e) from CompanyEvent e where e.deleted = false and e.eventTypeId in :ids "
            + "group by e.eventTypeId")
    List<Object[]> countByType(Collection<Long> ids);

    boolean existsByEventTypeIdAndDeletedFalse(Long eventTypeId);

    Optional<CompanyEvent> findByIdAndDeletedFalse(Long id);
}
