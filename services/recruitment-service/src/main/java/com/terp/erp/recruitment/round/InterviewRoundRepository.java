package com.terp.erp.recruitment.round;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.recruitment.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Long> {
    /** Rounds of live postings only; keyword matches name or description (the caller escapes % and _ with !). */
    String FILTERS = "from InterviewRound r where r.companyId = :companyId and r.deleted = false "
            + "and exists (select 1 from JobPosting p where p.id = r.jobPostingId and p.deleted = false) "
            + "and (:jobPostingId is null or r.jobPostingId = :jobPostingId) "
            + "and (:kw = '' or lower(r.name) like :kw escape '!' or lower(r.description) like :kw escape '!')";

    @Query("select r " + FILTERS + " and (:status is null or r.status = :status)")
    Page<InterviewRound> search(Long companyId, Long jobPostingId, EStatus status, String kw, Pageable pageable);

    @Query("select r.status, count(r) " + FILTERS + " group by r.status")
    List<Object[]> countByStatus(Long companyId, Long jobPostingId, String kw);

    List<InterviewRound> findByJobPostingIdAndStatusAndDeletedFalseOrderBySequenceNumberAsc(Long jobPostingId,
                                                                                         EStatus status);

    Optional<InterviewRound> findByIdAndDeletedFalse(Long id);

    boolean existsByJobPostingIdAndSequenceNumberAndDeletedFalseAndIdNot(Long jobPostingId, int sequenceNumber, Long id);
}
