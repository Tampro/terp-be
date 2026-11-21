package com.terp.erp.recruitment.posting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    /** keyword matches title or job code; the caller escapes % and _ with !. */
    @Query("select p from JobPosting p where p.companyId = :companyId and p.deleted = false "
            + "and (:status is null or p.status = :status) and (:published is null or p.published = :published) "
            + "and (:kw = '' or lower(p.title) like :kw escape '!' or lower(p.jobCode) like :kw escape '!')")
    Page<JobPosting> search(Long companyId, JobPosting.EStatus status, Boolean published, String kw, Pageable pageable);

    List<JobPosting> findByCompanyIdAndDeletedFalseOrderByTitleAsc(Long companyId);

    /** Published and not past its deadline: what the public career page shows. */
    String OPEN = "from JobPosting p where p.companyId = :companyId and p.deleted = false and p.published = true "
            + "and p.status = com.terp.erp.recruitment.posting.JobPosting.EStatus.PUBLISHED "
            + "and (p.applicationDeadline is null or p.applicationDeadline >= :today) ";

    /**
     * Career page search. kw matches the title or a skill; anyType = false filters on jobTypeIds; the salary band
     * overlaps [minSalary, maxSalary]; positions within [minPositions, maxPositions].
     */
    @Query("select p " + OPEN
            + "and (:kw = '' or lower(p.title) like :kw escape '!' or exists (select s from JobPosting p2 "
            + "join p2.skills s where p2 = p and lower(s) like :kw escape '!')) "
            + "and (:locationId is null or p.locationId = :locationId) "
            + "and (:anyType = true or p.jobTypeId in :jobTypeIds) "
            + "and (:minSalary is null or p.maxSalary is null or p.maxSalary >= :minSalary) "
            + "and (:maxSalary is null or p.minSalary is null or p.minSalary <= :maxSalary) "
            + "and (:minPositions is null or p.positions >= :minPositions) "
            + "and (:maxPositions is null or p.positions <= :maxPositions)")
    Page<JobPosting> openJobs(Long companyId, java.time.LocalDate today, String kw, Long locationId, boolean anyType,
                              java.util.Collection<Long> jobTypeIds, java.math.BigDecimal minSalary,
                              java.math.BigDecimal maxSalary, Integer minPositions, Integer maxPositions,
                              Pageable pageable);

    @Query("select count(p) " + OPEN)
    long countOpen(Long companyId, java.time.LocalDate today);

    /** Similar Jobs: other open postings of the same job type or department. */
    @Query("select p " + OPEN + "and p.id <> :id and (p.jobTypeId = :jobTypeId "
            + "or (:departmentId is not null and p.departmentId = :departmentId)) order by p.featured desc, "
            + "p.publishDate desc")
    List<JobPosting> similarOpen(Long companyId, java.time.LocalDate today, Long id, Long jobTypeId, Long departmentId,
                                 Pageable pageable);

    Optional<JobPosting> findByIdAndDeletedFalse(Long id);

    boolean existsByJobTypeIdAndDeletedFalse(Long jobTypeId);

    boolean existsByLocationIdAndDeletedFalse(Long locationId);

    @Query(value = "SELECT next_value FROM recruitment_sequence WHERE company_id = :companyId AND name = :name FOR UPDATE",
            nativeQuery = true)
    Long lockSequence(Long companyId, String name);

    @Modifying
    @Query(value = "INSERT IGNORE INTO recruitment_sequence (company_id, name, next_value) VALUES (:companyId, :name, 1)",
            nativeQuery = true)
    void ensureSequence(Long companyId, String name);

    @Modifying
    @Query(value = "UPDATE recruitment_sequence SET next_value = next_value + 1 WHERE company_id = :companyId "
            + "AND name = :name", nativeQuery = true)
    void bumpSequence(Long companyId, String name);

    /** Dashboard: [count, sum of positions] of PUBLISHED postings whose deadline has not passed. */
    @Query("select count(p), coalesce(sum(p.positions), 0) from JobPosting p where p.companyId = :companyId "
            + "and p.deleted = false and p.status = com.terp.erp.recruitment.posting.JobPosting.EStatus.PUBLISHED "
            + "and (p.applicationDeadline is null or p.applicationDeadline >= :today)")
    List<Object[]> activeCounts(Long companyId, java.time.LocalDate today);
}
