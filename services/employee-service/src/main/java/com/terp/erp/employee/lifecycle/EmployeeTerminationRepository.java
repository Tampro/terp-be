package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeTerminationRepository extends JpaRepository<EmployeeTermination, Long> {
    /** Dates on the termination date; keyword on the employee or the reason; the caller escapes with !. */
    String FILTERS = "from EmployeeTermination t, com.terp.erp.employee.employee.Employee e "
            + "where e.id = t.employeeId and t.companyId = :companyId and t.deleted = false "
            + "and (:typeId is null or t.terminationTypeId = :typeId) "
            + "and (:from is null or t.terminationDate >= :from) and (:to is null or t.terminationDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(t.reason) like :kw escape '!')";

    @Query(value = "select t " + FILTERS + " and (:status is null or t.status = :status)",
            countQuery = "select count(t) " + FILTERS + " and (:status is null or t.status = :status)")
    Page<EmployeeTermination> search(Long companyId, Long typeId, LocalDate from, LocalDate to,
                                     EmployeeTermination.EStatus status, String kw, Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, Long typeId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeeTermination> findByIdAndDeletedFalse(Long id);

    boolean existsByTerminationTypeIdAndDeletedFalse(Long terminationTypeId);
}
