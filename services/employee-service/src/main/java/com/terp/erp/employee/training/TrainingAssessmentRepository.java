package com.terp.erp.employee.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainingAssessmentRepository extends JpaRepository<TrainingAssessment, Long> {
    /** keyword on the assessment or program name; the caller escapes with !. */
    String FILTERS = "from TrainingAssessment a, TrainingProgram p where p.id = a.programId "
            + "and a.companyId = :companyId and a.deleted = false "
            + "and (:programId is null or a.programId = :programId) "
            + "and (:kw = '' or lower(a.name) like :kw escape '!' or lower(p.name) like :kw escape '!')";

    @Query(value = "select a " + FILTERS + " and (:type is null or a.type = :type)",
            countQuery = "select count(a) " + FILTERS + " and (:type is null or a.type = :type)")
    Page<TrainingAssessment> search(Long companyId, Long programId, TrainingAssessment.EType type, String kw,
                                    Pageable pageable);

    @Query("select a.type, count(a) " + FILTERS + " group by a.type")
    List<Object[]> countByType(Long companyId, Long programId, String kw);

    Optional<TrainingAssessment> findByIdAndDeletedFalse(Long id);

    boolean existsByProgramIdAndDeletedFalse(Long programId);
}
