package com.terp.erp.employee.performance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeGoalRepository extends JpaRepository<EmployeeGoal, Long> {
    /** keyword on the title, target or the employee; the caller escapes with !. */
    String FILTERS = "from EmployeeGoal g, com.terp.erp.employee.employee.Employee e "
            + "where e.id = g.employeeId and g.companyId = :companyId and g.deleted = false "
            + "and (:employeeId is null or g.employeeId = :employeeId) "
            + "and (:goalTypeId is null or g.goalTypeId = :goalTypeId) "
            + "and (:kw = '' or lower(g.title) like :kw escape '!' or lower(g.target) like :kw escape '!' "
            + "or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!')";

    @Query(value = "select g " + FILTERS + " and (:status is null or g.status = :status)",
            countQuery = "select count(g) " + FILTERS + " and (:status is null or g.status = :status)")
    Page<EmployeeGoal> search(Long companyId, Long employeeId, Long goalTypeId, EmployeeGoal.EStatus status,
                              String kw, Pageable pageable);

    @Query("select g.status, count(g) " + FILTERS + " group by g.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, Long goalTypeId, String kw);

    Optional<EmployeeGoal> findByIdAndDeletedFalse(Long id);

    boolean existsByGoalTypeIdAndDeletedFalse(Long goalTypeId);
}
