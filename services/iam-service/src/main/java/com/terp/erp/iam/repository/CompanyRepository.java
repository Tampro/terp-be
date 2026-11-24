package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.model.Company;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByIdAndDeletedFalse(Long id);

    boolean existsByCodeAndDeletedFalse(String code);

    List<Company> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Page<Company> findByDeletedFalse(Pageable pageable);

    String FILTER = "c.deleted = false"
            + " and (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + "      or lower(c.code) like lower(concat('%', :keyword, '%'))"
            + "      or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%')))"
            + " and c.createdDate >= :from and c.createdDate < :to";

    /** Companies list: keyword on name/code/email, created-date range [from, to), optional status. */
    @Query("select c from Company c where " + FILTER + " and (:status is null or c.status = :status)")
    Page<Company> search(String keyword, Instant from, Instant to, ECompanyStatus status, Pageable pageable);

    /** Status tab counts with the same keyword/date filter: rows of [status, count]. */
    @Query("select c.status, count(c) from Company c where " + FILTER + " group by c.status")
    List<Object[]> countByStatus(String keyword, Instant from, Instant to);

    // ---- referral program (docs/analysis/06 §3.2.6)
    Optional<Company> findByReferralCodeAndDeletedFalse(String referralCode);

    String REFERRED = " from Company c where c.deleted = false and c.referredByCompanyId is not null"
            + " and (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + "   or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%')))";

    @Query(value = "select c" + REFERRED, countQuery = "select count(c)" + REFERRED)
    Page<Company> searchReferred(String keyword, Pageable pageable);

    @Query("select count(c) from Company c where c.deleted = false and c.referredByCompanyId is not null")
    long countReferred();

    @Query("select count(c) from Company c where c.deleted = false and c.referredByCompanyId is not null"
            + " and c.planId is not null")
    long countReferredWithPlan();

    @Query("select count(c) from Company c where c.deleted = false and c.referredByCompanyId is not null"
            + " and c.status = com.terp.erp.iam.constant.ECompanyStatus.ACTIVE")
    long countReferredActive();

    @Query("select count(c) from Company c where c.deleted = false and c.referredByCompanyId is not null"
            + " and c.createdDate >= :from")
    long countReferredSince(Instant from);

    @Query("select count(c) from Company c where c.deleted = false and c.referredByCompanyId = :companyId")
    long countReferredBy(Long companyId);

    /** Super admin dashboard: every live company (the platform has few enough to aggregate in memory). */
    List<Company> findAllByDeletedFalse();

    /** Plan-expiry seat job: companies whose trial or paid period has ended. */
    List<Company> findByDeletedFalseAndPlanExpiresAtLessThanEqual(Instant now);

    /** Retention job: deleted companies past the retention period and not anonymised yet, oldest first. */
    List<Company> findTop50ByDeletedTrueAndAnonymizedAtIsNullAndDeletedAtLessThanEqualOrderByDeletedAtAsc(
            Instant cutoff);

    /** Finance retention: deleted at least 10 years ago, finance services not signalled yet, oldest first. */
    List<Company> findTop50ByDeletedTrueAndFinanceAnonymizedAtIsNullAndDeletedAtLessThanEqualOrderByDeletedAtAsc(
            Instant cutoff);
}
