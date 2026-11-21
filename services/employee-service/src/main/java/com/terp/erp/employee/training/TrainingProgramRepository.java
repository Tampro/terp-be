package com.terp.erp.employee.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, Long> {
    /** keyword on the name or description; the caller escapes with !. */
    String FILTERS = "from TrainingProgram p where p.companyId = :companyId and p.deleted = false "
            + "and (:typeId is null or p.trainingTypeId = :typeId) "
            + "and (:kw = '' or lower(p.name) like :kw escape '!' or lower(p.description) like :kw escape '!')";

    @Query(value = "select p " + FILTERS + " and (:status is null or p.status = :status)",
            countQuery = "select count(p) " + FILTERS + " and (:status is null or p.status = :status)")
    Page<TrainingProgram> search(Long companyId, Long typeId, TrainingProgram.EStatus status, String kw,
                                 Pageable pageable);

    @Query("select p.status, count(p) " + FILTERS + " group by p.status")
    List<Object[]> countByStatus(Long companyId, Long typeId, String kw);

    List<TrainingProgram> findByCompanyIdAndStatusInAndDeletedFalseOrderByNameAsc(
            Long companyId, Collection<TrainingProgram.EStatus> statuses);

    Optional<TrainingProgram> findByIdAndDeletedFalse(Long id);

    @Query("select count(p) > 0 from TrainingProgram p where p.companyId = :companyId and p.deleted = false "
            + "and lower(p.name) = lower(:name) and p.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);

    boolean existsByTrainingTypeIdAndDeletedFalse(Long trainingTypeId);

    /** [typeId, count] for the Programs column. */
    @Query("select p.trainingTypeId, count(p) from TrainingProgram p where p.deleted = false "
            + "and p.trainingTypeId in :typeIds group by p.trainingTypeId")
    List<Object[]> countByType(Collection<Long> typeIds);
}
