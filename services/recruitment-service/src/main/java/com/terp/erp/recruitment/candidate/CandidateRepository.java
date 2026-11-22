package com.terp.erp.recruitment.candidate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    /** keyword matches first name, last name or email; the caller escapes % and _ with !. */
    String FILTERS = "from Candidate c where c.companyId = :companyId and c.deleted = false "
            + "and (:jobPostingId is null or c.jobPostingId = :jobPostingId) "
            + "and (:sourceId is null or c.sourceId = :sourceId) "
            + "and (:kw = '' or lower(c.firstName) like :kw escape '!' or lower(c.lastName) like :kw escape '!' "
            + "or lower(concat(c.firstName, ' ', c.lastName)) like :kw escape '!' or lower(c.email) like :kw escape '!')";

    @Query("select c " + FILTERS + " and (:status is null or c.status = :status)")
    Page<Candidate> search(Long companyId, Long jobPostingId, Long sourceId, Candidate.EStatus status, String kw,
                           Pageable pageable);

    /** [status, count] of the status tabs and kanban badges under the same filters. */
    @Query("select c.status, count(c) " + FILTERS + " group by c.status")
    List<Object[]> countByStatus(Long companyId, Long jobPostingId, Long sourceId, String kw);

    /** [posting id, count]: the Applications column of the job postings list. */
    @Query("select c.jobPostingId, count(c) from Candidate c where c.deleted = false and c.jobPostingId in :ids "
            + "group by c.jobPostingId")
    List<Object[]> countByPosting(Collection<Long> ids);

    Optional<Candidate> findByIdAndDeletedFalse(Long id);

    Optional<Candidate> findByTrackingIdAndDeletedFalse(String trackingId);

    boolean existsByTrackingId(String trackingId);

    @Query("select count(c) > 0 from Candidate c where c.jobPostingId = :jobPostingId and c.deleted = false "
            + "and lower(c.email) = lower(:email)")
    boolean alreadyApplied(Long jobPostingId, String email);

    boolean existsBySourceIdAndDeletedFalse(Long sourceId);

    boolean existsByJobPostingIdAndDeletedFalse(Long jobPostingId);

    long countByJobPostingIdAndDeletedFalse(Long jobPostingId);
}
