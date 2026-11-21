package com.terp.erp.employee.holiday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HolidayCategoryRepository extends JpaRepository<HolidayCategory, Long> {
    Optional<HolidayCategory> findByIdAndDeletedFalse(Long id);

    /** System categories plus the company's own. */
    @Query("select c from HolidayCategory c where c.deleted = false and (c.companyId is null or c.companyId = :companyId) "
            + "order by c.companyId asc nulls first, c.name asc")
    List<HolidayCategory> visibleTo(Long companyId);

    @Query("select count(c) > 0 from HolidayCategory c where c.deleted = false and lower(c.name) = lower(:name) "
            + "and c.id <> :id and (c.companyId is null or c.companyId = :companyId)")
    boolean nameTaken(Long companyId, String name, Long id);
}
