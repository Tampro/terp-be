package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface EmployeeAwardRepository extends JpaRepository<EmployeeAward, Long> {
    /** keyword matches the employee's name, email or code, or the gift; the caller escapes with !. */
    @Query(value = "select a from EmployeeAward a, com.terp.erp.employee.employee.Employee e "
            + "where e.id = a.employeeId and a.companyId = :companyId and a.deleted = false "
            + "and (:awardTypeId is null or a.awardTypeId = :awardTypeId) "
            + "and (:employeeId is null or a.employeeId = :employeeId) "
            + "and (:from is null or a.awardDate >= :from) and (:to is null or a.awardDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(a.gift) like :kw escape '!')")
    Page<EmployeeAward> search(Long companyId, Long awardTypeId, Long employeeId, LocalDate from, LocalDate to,
                               String kw, Pageable pageable);

    Optional<EmployeeAward> findByIdAndDeletedFalse(Long id);

    boolean existsByAwardTypeIdAndDeletedFalse(Long awardTypeId);
}
