package com.terp.erp.employee.holiday;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    /**
     * A holiday matches the range when it overlaps it. branchId keeps the holidays of that branch and the company-wide
     * ones (no branch). keyword matches name or description; the caller escapes % and _ with !.
     */
    @Query("select h from Holiday h where h.companyId = :companyId and h.deleted = false "
            + "and (:categoryId is null or h.categoryId = :categoryId) "
            + "and (:from is null or h.endDate >= :from) and (:to is null or h.startDate <= :to) "
            + "and (:branchId is null or h.branchIds is empty or :branchId member of h.branchIds) "
            + "and (:kw = '' or lower(h.name) like :kw escape '!' or lower(h.description) like :kw escape '!')")
    Page<Holiday> search(Long companyId, Long categoryId, LocalDate from, LocalDate to, Long branchId, String kw,
                         Pageable pageable);

    Optional<Holiday> findByIdAndDeletedFalse(Long id);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);
}
