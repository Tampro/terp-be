package com.terp.erp.employee.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainingTypeRepository extends JpaRepository<TrainingType, Long> {
    /** keyword on the name or description; the caller escapes with !. */
    @Query("select t from TrainingType t where t.companyId = :companyId and t.deleted = false "
            + "and (:branchId is null or t.branchId = :branchId) "
            + "and (:departmentId is null or :departmentId member of t.departmentIds) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.description) like :kw escape '!')")
    Page<TrainingType> search(Long companyId, Long branchId, Long departmentId, String kw, Pageable pageable);

    List<TrainingType> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    Optional<TrainingType> findByIdAndDeletedFalse(Long id);

    @Query("select count(t) > 0 from TrainingType t where t.companyId = :companyId and t.deleted = false "
            + "and lower(t.name) = lower(:name) and t.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
