package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeTripRepository extends JpaRepository<EmployeeTrip, Long> {
    /**
     * startFrom = start date on or after; endTo = end date on or before; keyword on the employee, destination or
     * purpose; the caller escapes with !.
     */
    String FILTERS = "from EmployeeTrip t, com.terp.erp.employee.employee.Employee e "
            + "where e.id = t.employeeId and t.companyId = :companyId and t.deleted = false "
            + "and (:employeeId is null or t.employeeId = :employeeId) "
            + "and (:startFrom is null or t.startDate >= :startFrom) and (:endTo is null or t.endDate <= :endTo) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(t.destination) like :kw escape '!' "
            + "or lower(t.purpose) like :kw escape '!')";

    @Query(value = "select t " + FILTERS + " and (:status is null or t.status = :status)",
            countQuery = "select count(t) " + FILTERS + " and (:status is null or t.status = :status)")
    Page<EmployeeTrip> search(Long companyId, Long employeeId, LocalDate startFrom, LocalDate endTo,
                              EmployeeTrip.EStatus status, String kw, Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, LocalDate startFrom, LocalDate endTo, String kw);

    Optional<EmployeeTrip> findByIdAndDeletedFalse(Long id);
}
