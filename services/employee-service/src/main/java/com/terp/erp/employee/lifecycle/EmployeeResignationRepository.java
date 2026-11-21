package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeResignationRepository extends JpaRepository<EmployeeResignation, Long> {
    /** Dates on the resignation date; keyword on the employee or the reason; the caller escapes with !. */
    String FILTERS = "from EmployeeResignation r, com.terp.erp.employee.employee.Employee e "
            + "where e.id = r.employeeId and r.companyId = :companyId and r.deleted = false "
            + "and (:employeeId is null or r.employeeId = :employeeId) "
            + "and (:from is null or r.resignationDate >= :from) and (:to is null or r.resignationDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(r.reason) like :kw escape '!')";

    @Query(value = "select r " + FILTERS + " and (:status is null or r.status = :status)",
            countQuery = "select count(r) " + FILTERS + " and (:status is null or r.status = :status)")
    Page<EmployeeResignation> search(Long companyId, Long employeeId, LocalDate from, LocalDate to,
                                     EmployeeResignation.EStatus status, String kw, Pageable pageable);

    @Query("select r.status, count(r) " + FILTERS + " group by r.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeeResignation> findByIdAndDeletedFalse(Long id);
}
