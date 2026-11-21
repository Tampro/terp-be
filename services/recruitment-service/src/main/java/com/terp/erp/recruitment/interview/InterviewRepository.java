package com.terp.erp.recruitment.interview;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    /** keyword matches the candidate's name or email; the caller escapes % and _ with !. */
    String FILTERS = "from Interview i, com.terp.erp.recruitment.candidate.Candidate c "
            + "where c.id = i.candidateId and i.companyId = :companyId and i.deleted = false "
            + "and (:candidateId is null or i.candidateId = :candidateId) "
            + "and (:from is null or i.interviewDate >= :from) and (:to is null or i.interviewDate <= :to) "
            + "and (:kw = '' or lower(concat(c.firstName, ' ', c.lastName)) like :kw escape '!' "
            + "or lower(c.email) like :kw escape '!')";

    @Query(value = "select i " + FILTERS + " and (:status is null or i.status = :status)",
            countQuery = "select count(i) " + FILTERS + " and (:status is null or i.status = :status)")
    Page<Interview> search(Long companyId, Long candidateId, LocalDate from, LocalDate to, Interview.EStatus status,
                           String kw, Pageable pageable);

    /** [status, count] for the tabs, summary cards and kanban badges. */
    @Query("select i.status, count(i) " + FILTERS + " group by i.status")
    List<Object[]> countByStatus(Long companyId, Long candidateId, LocalDate from, LocalDate to, String kw);

    /** Completed interviews without any feedback (the Pending Feedback card). */
    @Query("select count(i) " + FILTERS + " and i.status = com.terp.erp.recruitment.interview.Interview.EStatus.COMPLETED "
            + "and not exists (select f.id from InterviewFeedback f where f.interviewId = i.id)")
    long countPendingFeedback(Long companyId, Long candidateId, LocalDate from, LocalDate to, String kw);

    /** Scheduled interviews of the company on a day, for the conflict check. */
    @Query("select i from Interview i where i.companyId = :companyId and i.interviewDate = :date and i.deleted = false "
            + "and i.status = com.terp.erp.recruitment.interview.Interview.EStatus.SCHEDULED")
    List<Interview> scheduledOn(Long companyId, LocalDate date);

    Optional<Interview> findByIdAndDeletedFalse(Long id);

    boolean existsByRoundIdAndDeletedFalse(Long roundId);

    boolean existsByInterviewTypeIdAndDeletedFalse(Long interviewTypeId);

    /** A deleted candidate takes their interviews with them. */
    @Modifying
    @Query("update Interview i set i.deleted = true where i.candidateId = :candidateId and i.deleted = false")
    int deleteByCandidate(Long candidateId);
}
