package com.terp.erp.iam.referral;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReferralCommissionRepository extends JpaRepository<ReferralCommission, Long> {
    boolean existsByOrderId(Long orderId);

    @Query("select coalesce(sum(c.amount), 0) from ReferralCommission c where c.referrerCompanyId = :companyId")
    BigDecimal totalEarned(Long companyId);

    @Query("select coalesce(sum(c.amount), 0) from ReferralCommission c")
    BigDecimal totalAll();

    /** Rows of [referredCompanyId, sum(amount)] for a page of referred companies. */
    @Query("select c.referredCompanyId, sum(c.amount) from ReferralCommission c"
            + " where c.referredCompanyId in :ids group by c.referredCompanyId")
    List<Object[]> totalsByReferred(Collection<Long> ids);

    List<ReferralCommission> findByReferredCompanyIdInOrderByIdDesc(Collection<Long> ids);

    /** Top referrers: rows of [Company, referral count, commission total]. */
    @Query("select r, (select count(x) from Company x where x.referredByCompanyId = r.id and x.deleted = false),"
            + " (select coalesce(sum(c.amount), 0) from ReferralCommission c where c.referrerCompanyId = r.id)"
            + " from Company r where r.deleted = false and exists"
            + " (select 1 from Company x where x.referredByCompanyId = r.id and x.deleted = false)"
            + " order by 2 desc, 3 desc")
    List<Object[]> topReferrers(Pageable pageable);
}
