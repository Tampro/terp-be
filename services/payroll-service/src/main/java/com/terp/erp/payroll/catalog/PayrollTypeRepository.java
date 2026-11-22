package com.terp.erp.payroll.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PayrollTypeRepository extends JpaRepository<PayrollType, Long> {
    @Query("select t from PayrollType t where t.companyId = :companyId and t.kind = :kind and t.deleted = false "
            + "and (:active is null or t.active = :active) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.code) like :kw escape '!')")
    Page<PayrollType> search(Long companyId, PayrollType.EKind kind, Boolean active, String kw, Pageable pageable);

    Optional<PayrollType> findByIdAndDeletedFalse(Long id);

    @Query("select count(t) > 0 from PayrollType t where t.companyId = :companyId and t.kind = :kind "
            + "and t.deleted = false and lower(t.code) = lower(:code) and t.id <> :id")
    boolean codeTaken(Long companyId, PayrollType.EKind kind, String code, Long id);
}
