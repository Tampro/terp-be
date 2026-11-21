package com.terp.erp.employee.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {
    Optional<Employee> findByIdAndDeletedFalse(Long id);

    Optional<Employee> findFirstByCompanyIdAndUserIdAndDeletedFalse(Long companyId, Long userId);

    boolean existsByCompanyIdAndEmailAndDeletedFalseAndIdNot(Long companyId, String email, Long id);

    boolean existsByCompanyIdAndEmployeeCodeAndDeletedFalseAndIdNot(Long companyId, String code, Long id);

    long countByCompanyIdAndDeletedFalse(Long companyId);

    boolean existsByBranchIdAndDeletedFalse(Long branchId);

    long countByBranchIdAndDeletedFalse(Long branchId);

    boolean existsByDepartmentIdAndDeletedFalse(Long departmentId);

    long countByDepartmentIdAndDeletedFalse(Long departmentId);

    boolean existsByDesignationIdAndDeletedFalse(Long designationId);

    long countByDesignationIdAndDeletedFalse(Long designationId);

    /** Takes the next "EMP" number of the company; the row lock serialises concurrent creates. */
    @Query(value = "SELECT next_value FROM employee_sequence WHERE company_id = :companyId FOR UPDATE",
            nativeQuery = true)
    Long lockSequence(Long companyId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "INSERT IGNORE INTO employee_sequence (company_id, next_value) VALUES (:companyId, 1)",
            nativeQuery = true)
    void ensureSequence(Long companyId);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE employee_sequence SET next_value = next_value + 1 WHERE company_id = :companyId",
            nativeQuery = true)
    void bumpSequence(Long companyId);

    List<Employee> findByCompanyIdAndDeletedFalse(Long companyId);

    List<Employee> findByReportsToIdAndDeletedFalse(Long reportsToId);
}
