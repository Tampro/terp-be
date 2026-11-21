package com.terp.erp.employee.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeTrainingRepository extends JpaRepository<EmployeeTraining, Long> {
    /** Dates on the assigned date; keyword on the employee or the program; the caller escapes with !. */
    String FILTERS = "from EmployeeTraining t, com.terp.erp.employee.employee.Employee e, TrainingProgram p "
            + "where e.id = t.employeeId and p.id = t.programId and t.companyId = :companyId and t.deleted = false "
            + "and (:programId is null or t.programId = :programId) "
            + "and (:from is null or t.assignedDate >= :from) and (:to is null or t.assignedDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(p.name) like :kw escape '!')";

    @Query(value = "select t " + FILTERS + " and (:status is null or t.status = :status)",
            countQuery = "select count(t) " + FILTERS + " and (:status is null or t.status = :status)")
    Page<EmployeeTraining> search(Long companyId, Long programId, LocalDate from, LocalDate to,
                                  EmployeeTraining.EStatus status, String kw, Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, Long programId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeeTraining> findByIdAndDeletedFalse(Long id);

    boolean existsByProgramIdAndEmployeeIdAndDeletedFalseAndIdNot(Long programId, Long employeeId, Long id);

    /** The program's trainees for session attendance: every assignment except failed ones. */
    @Query("select t from EmployeeTraining t where t.programId = :programId and t.deleted = false "
            + "and t.status <> com.terp.erp.employee.training.EmployeeTraining.EStatus.FAILED")
    List<EmployeeTraining> trainees(Long programId);

    List<EmployeeTraining> findByProgramIdAndEmployeeIdInAndDeletedFalse(Long programId, Collection<Long> employeeIds);

    /** Seats taken: every assignment except failed ones. */
    @Query("select count(t) from EmployeeTraining t where t.programId = :programId and t.deleted = false "
            + "and t.status <> com.terp.erp.employee.training.EmployeeTraining.EStatus.FAILED and t.id <> :exceptId")
    long seatsTaken(Long programId, Long exceptId);

    boolean existsByProgramIdAndDeletedFalse(Long programId);

    /** [programId, status, count] for the program cards and the dashboard. */
    @Query("select t.programId, t.status, count(t) from EmployeeTraining t where t.deleted = false "
            + "and t.programId in :programIds group by t.programId, t.status")
    List<Object[]> countByProgram(Collection<Long> programIds);

    @Query("select t.programId, t.status, count(t) from EmployeeTraining t where t.deleted = false "
            + "and t.companyId = :companyId group by t.programId, t.status")
    List<Object[]> countByProgramOfCompany(Long companyId);

    List<EmployeeTraining> findTop5ByCompanyIdAndStatusAndDeletedFalseOrderByCompletionDateDescIdDesc(
            Long companyId, EmployeeTraining.EStatus status);

    List<EmployeeTraining> findTop5ByCompanyIdAndStatusAndAssignedDateGreaterThanEqualAndDeletedFalseOrderByAssignedDateAscIdAsc(
            Long companyId, EmployeeTraining.EStatus status, LocalDate from);
}
