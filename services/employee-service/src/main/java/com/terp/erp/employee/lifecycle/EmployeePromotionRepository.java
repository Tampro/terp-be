package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeePromotionRepository extends JpaRepository<EmployeePromotion, Long> {
    /** designationId = the new designation; dates on the promotion date; keyword on the employee or the reason. */
    String FILTERS = "from EmployeePromotion p, com.terp.erp.employee.employee.Employee e "
            + "where e.id = p.employeeId and p.companyId = :companyId and p.deleted = false "
            + "and (:designationId is null or p.newDesignationId = :designationId) "
            + "and (:from is null or p.promotionDate >= :from) and (:to is null or p.promotionDate <= :to) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(p.reason) like :kw escape '!')";

    @Query(value = "select p " + FILTERS + " and (:status is null or p.status = :status)",
            countQuery = "select count(p) " + FILTERS + " and (:status is null or p.status = :status)")
    Page<EmployeePromotion> search(Long companyId, Long designationId, LocalDate from, LocalDate to, EDecision status,
                                   String kw, Pageable pageable);

    @Query("select p.status, count(p) " + FILTERS + " group by p.status")
    List<Object[]> countByStatus(Long companyId, Long designationId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeePromotion> findByIdAndDeletedFalse(Long id);
}
