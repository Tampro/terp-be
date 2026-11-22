package com.terp.erp.employee.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainerRepository extends JpaRepository<Trainer, Long> {
    /** keyword on the name, email, expertise or qualification; the caller escapes with !. */
    @Query("select t from Trainer t where t.companyId = :companyId and t.deleted = false "
            + "and (:branchId is null or t.branchId = :branchId) "
            + "and (:departmentId is null or t.departmentId = :departmentId) "
            + "and (:active is null or t.active = :active) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.email) like :kw escape '!' "
            + "or lower(t.expertise) like :kw escape '!' or lower(t.qualification) like :kw escape '!')")
    Page<Trainer> search(Long companyId, Long branchId, Long departmentId, Boolean active, String kw,
                         Pageable pageable);

    List<Trainer> findByCompanyIdAndActiveTrueAndDeletedFalseOrderByNameAsc(Long companyId);

    Optional<Trainer> findByIdAndDeletedFalse(Long id);
}
