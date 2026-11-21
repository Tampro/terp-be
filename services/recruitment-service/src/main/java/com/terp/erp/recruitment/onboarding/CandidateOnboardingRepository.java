package com.terp.erp.recruitment.onboarding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CandidateOnboardingRepository extends JpaRepository<CandidateOnboarding, Long> {
    /** keyword matches the employee's name or email; the caller escapes with !. */
    String FILTERS = "from CandidateOnboarding o where o.companyId = :companyId and o.deleted = false "
            + "and (:employeeId is null or o.employeeId = :employeeId) "
            + "and (:kw = '' or lower(o.employeeName) like :kw escape '!' or lower(o.employeeEmail) like :kw escape '!')";

    @Query("select o " + FILTERS + " and (:status is null or o.status = :status)")
    Page<CandidateOnboarding> search(Long companyId, Long employeeId, CandidateOnboarding.EStatus status, String kw,
                                     Pageable pageable);

    @Query("select o.status, count(o) " + FILTERS + " group by o.status")
    List<Object[]> countByStatus(Long companyId, Long employeeId, String kw);

    Optional<CandidateOnboarding> findByIdAndDeletedFalse(Long id);

    @Query("select count(o) > 0 from CandidateOnboarding o where o.companyId = :companyId and o.deleted = false "
            + "and o.employeeId = :employeeId and o.id <> :id")
    boolean employeeTaken(Long companyId, Long employeeId, Long id);

    boolean existsByChecklistIdAndDeletedFalse(Long checklistId);
}
