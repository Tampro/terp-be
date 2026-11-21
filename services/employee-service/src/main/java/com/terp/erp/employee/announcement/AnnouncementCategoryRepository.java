package com.terp.erp.employee.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnnouncementCategoryRepository extends JpaRepository<AnnouncementCategory, Long> {
    Optional<AnnouncementCategory> findByIdAndDeletedFalse(Long id);

    /** System categories plus the company's own. */
    @Query("select c from AnnouncementCategory c where c.deleted = false "
            + "and (c.companyId is null or c.companyId = :companyId) order by c.companyId asc nulls first, c.name asc")
    List<AnnouncementCategory> visibleTo(Long companyId);

    @Query("select count(c) > 0 from AnnouncementCategory c where c.deleted = false and lower(c.name) = lower(:name) "
            + "and c.id <> :id and (c.companyId is null or c.companyId = :companyId)")
    boolean nameTaken(Long companyId, String name, Long id);
}
