package com.terp.erp.payroll.component;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {
    /** keyword on the name or description; the caller escapes with !. */
    @Query("select c from SalaryComponent c where c.companyId = :companyId and c.deleted = false "
            + "and (:type is null or c.type = :type) and (:calculation is null or c.calculation = :calculation) "
            + "and (:status is null or c.status = :status) "
            + "and (:kw = '' or lower(c.name) like :kw escape '!' or lower(c.description) like :kw escape '!')")
    Page<SalaryComponent> search(Long companyId, SalaryComponent.EType type, SalaryComponent.ECalculation calculation,
                                 SalaryComponent.EStatus status, String kw, Pageable pageable);

    /** EARNING sorts after DEDUCTION as text, so descending puts the earnings first. */
    List<SalaryComponent> findByCompanyIdAndStatusAndDeletedFalseOrderByTypeDescNameAsc(
            Long companyId, SalaryComponent.EStatus status);

    Optional<SalaryComponent> findByIdAndDeletedFalse(Long id);

    @Query("select count(c) > 0 from SalaryComponent c where c.companyId = :companyId and c.deleted = false "
            + "and lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
