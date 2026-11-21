package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmployeeWarningRepository extends JpaRepository<EmployeeWarning, Long> {
    /** Nightly expiry: warnings in force whose expiry date has passed become EXPIRED. Returns how many. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update EmployeeWarning w set w.status = "
            + "com.terp.erp.employee.lifecycle.EmployeeWarning.EStatus.EXPIRED, w.updatedDate = :now, "
            + "w.updatedBy = 'system' where w.deleted = false and w.expiryDate < :today and w.status in :inForce")
    int expireOverdue(java.time.LocalDate today, java.util.Collection<EmployeeWarning.EStatus> inForce,
                      java.time.Instant now);

    /** keyword matches the employee's name / email / no. or the subject; the caller escapes with !. */
    String FILTERS = "from EmployeeWarning w, com.terp.erp.employee.employee.Employee e "
            + "where e.id = w.employeeId and w.companyId = :companyId and w.deleted = false "
            + "and (:employeeId is null or w.employeeId = :employeeId) "
            + "and (:typeId is null or w.warningTypeId = :typeId) "
            + "and (:severityId is null or w.severityId = :severityId) "
            + "and (:kw = '' or lower(e.fullName) like :kw escape '!' or lower(e.email) like :kw escape '!' "
            + "or lower(e.employeeNo) like :kw escape '!' or lower(w.subject) like :kw escape '!')";

    @Query(value = "select w " + FILTERS + " and (:status is null or w.status = :status)",
            countQuery = "select count(w) " + FILTERS + " and (:status is null or w.status = :status)")
    Page<EmployeeWarning> search(Long companyId, Long employeeId, Long typeId, Long severityId,
                                 EmployeeWarning.EStatus status, String kw, Pageable pageable);

    @Query("select w.status, count(w) " + FILTERS + " group by w.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, Long typeId, Long severityId, String kw);

    Optional<EmployeeWarning> findByIdAndDeletedFalse(Long id);

    boolean existsByWarningTypeIdAndDeletedFalse(Long warningTypeId);

    boolean existsBySeverityIdAndDeletedFalse(Long severityId);
}
