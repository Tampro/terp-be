package com.terp.erp.employee.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeComplaintRepository extends JpaRepository<EmployeeComplaint, Long> {
    /**
     * Dates on the complaint date; keyword on the subject or the accused (never on an anonymous complainant, so a
     * search cannot reveal them); the caller escapes with !.
     */
    String FILTERS = "from EmployeeComplaint c left join com.terp.erp.employee.employee.Employee a "
            + "on a.id = c.againstId "
            + "where c.companyId = :companyId and c.deleted = false "
            + "and (:typeId is null or c.complaintTypeId = :typeId) "
            + "and (:from is null or c.complaintDate >= :from) and (:to is null or c.complaintDate <= :to) "
            + "and (:kw = '' or lower(c.subject) like :kw escape '!' or lower(a.fullName) like :kw escape '!' "
            + "or lower(a.email) like :kw escape '!' or (c.anonymous = false and exists (select 1 from "
            + "com.terp.erp.employee.employee.Employee p where p.id = c.complainantId and (lower(p.fullName) like "
            + ":kw escape '!' or lower(p.email) like :kw escape '!'))))";

    @Query(value = "select c " + FILTERS + " and (:status is null or c.status = :status)",
            countQuery = "select count(c) " + FILTERS + " and (:status is null or c.status = :status)")
    Page<EmployeeComplaint> search(Long companyId, Long typeId, LocalDate from, LocalDate to,
                                   EmployeeComplaint.EStatus status, String kw, Pageable pageable);

    @Query("select c.status, count(c) " + FILTERS + " group by c.status")
    List<Object[]> countByStatus(Long companyId, Long typeId, LocalDate from, LocalDate to, String kw);

    Optional<EmployeeComplaint> findByIdAndDeletedFalse(Long id);

    boolean existsByComplaintTypeIdAndDeletedFalse(Long complaintTypeId);

    boolean existsByResolutionTypeIdAndDeletedFalse(Long resolutionTypeId);
}
