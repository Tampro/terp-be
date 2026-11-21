package com.terp.erp.employee.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {
    List<AssessmentResult> findByAssessmentIdOrderByScoreDescIdAsc(Long assessmentId);

    List<AssessmentResult> findByEmployeeTrainingIdOrderByIdAsc(Long employeeTrainingId);

    Optional<AssessmentResult> findByIdAndAssessmentId(Long id, Long assessmentId);

    boolean existsByAssessmentIdAndEmployeeTrainingIdAndIdNot(Long assessmentId, Long employeeTrainingId, Long id);

    boolean existsByAssessmentId(Long assessmentId);

    boolean existsByEmployeeTrainingId(Long employeeTrainingId);

    /** [assessmentId, count] for the Results column. */
    @Query("select r.assessmentId, count(r) from AssessmentResult r where r.assessmentId in :ids group by r.assessmentId")
    List<Object[]> countByAssessment(Collection<Long> ids);
}
