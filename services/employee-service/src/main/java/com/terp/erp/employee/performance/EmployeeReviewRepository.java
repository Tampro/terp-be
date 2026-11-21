package com.terp.erp.employee.performance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeReviewRepository extends JpaRepository<EmployeeReview, Long> {
    /** Dates on the review date; keyword on the employee or the reviewer; the caller escapes with !. */
    String FILTERS = "from EmployeeReview r, com.terp.erp.employee.employee.Employee e, "
            + "com.terp.erp.employee.employee.Employee v "
            + "where e.id = r.employeeId and v.id = r.reviewerId and r.companyId = :companyId and r.deleted = false "
            + "and (:cycleId is null or r.reviewCycleId = :cycleId) "
            + "and (:from is null or r.reviewDate >= :from) and (:to is null or r.reviewDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(v.fullName) like :kw escape '!' "
            + "or lower(v.email) like :kw escape '!')";

    @Query(value = "select r " + FILTERS + " and (:status is null or r.status = :status)",
            countQuery = "select count(r) " + FILTERS + " and (:status is null or r.status = :status)")
    Page<EmployeeReview> search(Long companyId, Long cycleId, LocalDate from, LocalDate to,
                                EmployeeReview.EStatus status, String kw, Pageable pageable);

    @Query("select r.status, count(r) " + FILTERS + " group by r.status")
    List<Object[]> countByStatus(Long companyId, Long cycleId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeeReview> findByIdAndDeletedFalse(Long id);

    boolean existsByReviewCycleIdAndDeletedFalse(Long reviewCycleId);
}
