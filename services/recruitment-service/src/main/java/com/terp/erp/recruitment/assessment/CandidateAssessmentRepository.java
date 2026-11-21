package com.terp.erp.recruitment.assessment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CandidateAssessmentRepository extends JpaRepository<CandidateAssessment, Long> {
    /** keyword matches the assessment name or the candidate's name / email; the caller escapes with !. */
    String FILTERS = "from CandidateAssessment a, com.terp.erp.recruitment.candidate.Candidate c "
            + "where c.id = a.candidateId and a.companyId = :companyId and a.deleted = false "
            + "and (:candidateId is null or a.candidateId = :candidateId) "
            + "and (:kw = '' or lower(a.name) like :kw escape '!' "
            + "or lower(concat(c.firstName, ' ', c.lastName)) like :kw escape '!' or lower(c.email) like :kw escape '!')";

    @Query(value = "select a " + FILTERS + " and (:status is null or a.status = :status)",
            countQuery = "select count(a) " + FILTERS + " and (:status is null or a.status = :status)")
    Page<CandidateAssessment> search(Long companyId, Long candidateId, CandidateAssessment.EStatus status, String kw,
                                     Pageable pageable);

    @Query("select a.status, count(a) " + FILTERS + " group by a.status")
    List<Object[]> countByStatus(Long companyId, Long candidateId, String kw);

    Optional<CandidateAssessment> findByIdAndDeletedFalse(Long id);

    @Modifying
    @Query("update CandidateAssessment a set a.deleted = true where a.candidateId = :candidateId and a.deleted = false")
    int deleteByCandidate(Long candidateId);
}
