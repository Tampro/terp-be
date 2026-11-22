package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeTransferRepository extends JpaRepository<EmployeeTransfer, Long> {
    /** departmentId matches the department left or joined; dates on the transfer date; keyword on the employee. */
    String FILTERS = "from EmployeeTransfer t, com.terp.erp.employee.employee.Employee e "
            + "where e.id = t.employeeId and t.companyId = :companyId and t.deleted = false "
            + "and (:departmentId is null or t.fromDepartmentId = :departmentId or t.toDepartmentId = :departmentId) "
            + "and (:from is null or t.transferDate >= :from) and (:to is null or t.transferDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(t.reason) like :kw escape '!')";

    @Query(value = "select t " + FILTERS + " and (:status is null or t.status = :status)",
            countQuery = "select count(t) " + FILTERS + " and (:status is null or t.status = :status)")
    Page<EmployeeTransfer> search(Long companyId, Long departmentId, LocalDate from, LocalDate to, ETransferStatus status,
                                  String kw, Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, Long departmentId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeeTransfer> findByIdAndDeletedFalse(Long id);
}
