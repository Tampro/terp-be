package com.terp.erp.employee.announcement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    /**
     * Admin list. branchId / departmentId keep the announcements that reach it (targeted or company-wide); from/to keep
     * the ones overlapping the range; startAfter keeps the upcoming ones. keyword matches title or short description;
     * the caller escapes % and _ with !.
     */
    @Query("select a from Announcement a where a.companyId = :companyId and a.deleted = false "
            + "and (:categoryId is null or a.categoryId = :categoryId) "
            + "and (:branchId is null or a.companyWide = true or :branchId member of a.branchIds) "
            + "and (:departmentId is null or a.companyWide = true or :departmentId member of a.departmentIds) "
            + "and (:from is null or a.endDate >= :from) and (:to is null or a.startDate <= :to) "
            + "and (:featured is null or a.featured = :featured) "
            + "and (:highPriority is null or a.highPriority = :highPriority) "
            + "and (:startAfter is null or a.startDate > :startAfter) "
            + "and (:kw = '' or lower(a.title) like :kw escape '!' or lower(a.shortDescription) like :kw escape '!')")
    Page<Announcement> search(Long companyId, Long categoryId, Long branchId, Long departmentId, LocalDate from,
                              LocalDate to, Boolean featured, Boolean highPriority, LocalDate startAfter, String kw,
                              Pageable pageable);

    /** Employee feed: active today and reaching the employee's branch or department (union), or company-wide. */
    @Query("select a from Announcement a where a.companyId = :companyId and a.deleted = false "
            + "and a.startDate <= :today and a.endDate >= :today "
            + "and (a.companyWide = true or (:branchId is not null and :branchId member of a.branchIds) "
            + "or (:departmentId is not null and :departmentId member of a.departmentIds)) "
            + "and (:featured is null or a.featured = :featured) "
            + "and (:highPriority is null or a.highPriority = :highPriority) "
            + "and (:kw = '' or lower(a.title) like :kw escape '!' or lower(a.shortDescription) like :kw escape '!')")
    Page<Announcement> feed(Long companyId, Long branchId, Long departmentId, LocalDate today, Boolean featured,
                            Boolean highPriority, String kw, Pageable pageable);

    Optional<Announcement> findByIdAndDeletedFalse(Long id);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    /**
     * Employees the announcement reaches today: not deleted, not terminated, in a target branch or department.
     * TODO(assumption): Total Employees counts the current audience, not a snapshot at publication — confirm with BA.
     */
    @Query("select count(e) from Employee e where e.companyId = :companyId and e.deleted = false "
            + "and e.status <> com.terp.erp.employee.employee.EEmployeeStatus.TERMINATED "
            + "and (:companyWide = true or e.branchId in :branchIds or e.departmentId in :departmentIds)")
    long countAudience(Long companyId, boolean companyWide, Collection<Long> branchIds, Collection<Long> departmentIds);

    @Query("select v.announcementId, count(v) from AnnouncementView v where v.announcementId in :ids "
            + "group by v.announcementId")
    List<Object[]> countViews(Collection<Long> ids);
}
