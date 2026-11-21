package com.terp.erp.payroll.salary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeSalaryRepository extends JpaRepository<EmployeeSalary, Long> {
    /** keyword on the copied employee name / no. / email; the caller escapes with !. */
    String FILTERS = "from EmployeeSalary s where s.companyId = :companyId and s.deleted = false "
            + "and (:employeeId is null or s.employeeId = :employeeId) "
            + "and (:kw = '' or lower(s.employeeName) like :kw escape '!' or lower(s.employeeNo) like :kw escape '!' "
            + "or lower(s.employeeEmail) like :kw escape '!')";

    @Query(value = "select s " + FILTERS + " and (:status is null or s.status = :status)",
            countQuery = "select count(s) " + FILTERS + " and (:status is null or s.status = :status)")
    Page<EmployeeSalary> search(Long companyId, Long employeeId, EmployeeSalary.EStatus status, String kw,
                                Pageable pageable);

    @Query("select s.status, count(s) " + FILTERS + " group by s.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, String kw);

    Optional<EmployeeSalary> findByIdAndDeletedFalse(Long id);

    boolean existsByEmployeeIdAndStatusAndDeletedFalseAndIdNot(Long employeeId, EmployeeSalary.EStatus status,
                                                               Long id);

    /** The structures a payroll run processes. */
    List<EmployeeSalary> findByCompanyIdAndStatusAndDeletedFalse(Long companyId, EmployeeSalary.EStatus status);

    @Query("select count(s) > 0 from EmployeeSalary s join s.lines l where s.deleted = false "
            + "and l.componentId = :componentId")
    boolean usesComponent(Long componentId);
}
