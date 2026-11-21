package com.terp.erp.iam.referral;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {
    Page<PayoutRequest> findByCompanyId(Long companyId, Pageable pageable);

    boolean existsByCompanyIdAndStatus(Long companyId, EPayoutStatus status);

    /** Money no longer available to request: pending (reserved) + approved (paid). */
    @Query("select coalesce(sum(p.amount), 0) from PayoutRequest p where p.companyId = :companyId"
            + " and p.status in :statuses")
    BigDecimal sumByCompanyAndStatus(Long companyId, Collection<EPayoutStatus> statuses);

    @Query("select coalesce(sum(p.amount), 0) from PayoutRequest p where p.status = :status")
    BigDecimal sumByStatus(EPayoutStatus status);

    @Query("select coalesce(sum(p.amount), 0) from PayoutRequest p where p.status = :status"
            + " and p.processedAt >= :from")
    BigDecimal sumProcessedSince(EPayoutStatus status, Instant from);

    String FILTER = " from PayoutRequest p join Company c on c.id = p.companyId"
            + " where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + "   or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%')))";

    /** Rows of [PayoutRequest, company name, company email]. */
    @Query(value = "select p, c.name, c.email" + FILTER + " and (:status is null or p.status = :status)",
            countQuery = "select count(p)" + FILTER + " and (:status is null or p.status = :status)")
    Page<Object[]> searchWithNames(String keyword, EPayoutStatus status, Pageable pageable);

    @Query("select p.status, count(p)" + FILTER + " group by p.status")
    List<Object[]> countByStatus(String keyword);
}
