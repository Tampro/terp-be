package com.terp.erp.document.hr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcknowledgmentRepository extends JpaRepository<Acknowledgment, Long> {
    /** Live acknowledgments of live documents; keyword on employee name / email; the caller escapes with !. */
    String FILTERS = "from Acknowledgment a where a.companyId = :companyId and a.deleted = false "
            + "and exists (select 1 from HrDocument d where d.id = a.documentId and d.deleted = false) "
            + "and (:documentId is null or a.documentId = :documentId) "
            + "and (:employeeId is null or a.employeeId = :employeeId) "
            + "and (:kw = '' or lower(a.employeeName) like :kw escape '!' or lower(a.employeeEmail) like :kw escape '!')";

    /** Pending past its due date. */
    String OVERDUE = "(a.status = com.terp.erp.document.hr.Acknowledgment.EStatus.PENDING "
            + "and a.dueDate is not null and a.dueDate < :today)";

    /** stage null = any; overdue null = any, true = only overdue, false = none overdue. */
    String TAB = " and (:stage is null or a.status = :stage) and (:overdue is null "
            + "or (:overdue = true and " + OVERDUE + ") or (:overdue = false and not " + OVERDUE + "))";

    @Query(value = "select a " + FILTERS + TAB, countQuery = "select count(a) " + FILTERS + TAB)
    Page<Acknowledgment> search(Long companyId, Long documentId, Long employeeId, String kw,
                                Acknowledgment.EStatus stage, Boolean overdue, LocalDate today, Pageable pageable);

    /** [stage, overdue, count]. */
    @Query("select a.status, case when " + OVERDUE + " then true else false end, count(a) " + FILTERS
            + " group by a.status, case when " + OVERDUE + " then true else false end")
    List<Object[]> countTabs(Long companyId, Long documentId, Long employeeId, String kw, LocalDate today);

    /** My Acknowledgments: assigned to this IAM user. */
    @Query("select a from Acknowledgment a where a.companyId = :companyId and a.employeeUserId = :userId "
            + "and a.deleted = false and exists (select 1 from HrDocument d where d.id = a.documentId "
            + "and d.deleted = false) order by a.status, a.dueDate")
    Page<Acknowledgment> mine(Long companyId, Long userId, Pageable pageable);

    boolean existsByDocumentIdAndEmployeeIdAndStatusAndDeletedFalseAndIdNot(Long documentId, Long employeeId,
                                                                             Acknowledgment.EStatus status, Long id);

    Optional<Acknowledgment> findByIdAndDeletedFalse(Long id);

    /** The download gate: the caller still has to acknowledge this document. */
    boolean existsByDocumentIdAndEmployeeUserIdAndStatusAndDeletedFalse(Long documentId, Long employeeUserId,
                                                                       Acknowledgment.EStatus status);
}
