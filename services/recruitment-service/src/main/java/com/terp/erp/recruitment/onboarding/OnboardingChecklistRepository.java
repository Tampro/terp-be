package com.terp.erp.recruitment.onboarding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.recruitment.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface OnboardingChecklistRepository extends JpaRepository<OnboardingChecklist, Long> {
    String FILTERS = "from OnboardingChecklist c where c.companyId = :companyId and c.deleted = false "
            + "and (:kw = '' or lower(c.name) like :kw escape '!')";

    @Query("select c " + FILTERS + " and (:status is null or c.status = :status)")
    Page<OnboardingChecklist> search(Long companyId, EStatus status, String kw, Pageable pageable);

    @Query("select c.status, count(c) " + FILTERS + " group by c.status")
    List<Object[]> countByStatus(Long companyId, String kw);

    List<OnboardingChecklist> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    Optional<OnboardingChecklist> findByIdAndDeletedFalse(Long id);

    @Query("select count(c) > 0 from OnboardingChecklist c where c.companyId = :companyId and c.deleted = false "
            + "and lower(c.name) = lower(:name) and c.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);

    /** Clears the default flag of the company's other checklists. */
    @Modifying
    @Query("update OnboardingChecklist c set c.defaultChecklist = false where c.companyId = :companyId "
            + "and c.id <> :id and c.defaultChecklist = true")
    int clearDefault(Long companyId, Long id);
}
