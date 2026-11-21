package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.saas.EOrderStatus;

import java.util.Collection;

public interface PlanOrderRepository extends JpaRepository<PlanOrder, Long> {
    java.util.Optional<PlanOrder> findByStripeSessionId(String stripeSessionId);

    Page<PlanOrder> findByCompanyId(Long companyId, Pageable pageable);

    Page<PlanOrder> findByStatus(EOrderStatus status, Pageable pageable);

    boolean existsByCompanyIdAndStatus(Long companyId, EOrderStatus status);

    long countByCouponIdAndStatusIn(Long couponId, Collection<EOrderStatus> statuses);

    long countByCouponIdAndCompanyIdAndStatusIn(Long couponId, Long companyId, Collection<EOrderStatus> statuses);

    /** Keyword matches order number, company name/code/email; order date in [from, to). */
    String FILTER = " from PlanOrder o join Company c on c.id = o.companyId join Plan p on p.id = o.planId"
            + " where (:keyword = '' or lower(o.orderNumber) like lower(concat('%', :keyword, '%'))"
            + "   or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + "   or lower(c.code) like lower(concat('%', :keyword, '%'))"
            + "   or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%')))"
            + " and o.createdDate >= :from and o.createdDate < :to";

    /** Rows of [PlanOrder, company name, company email, plan name] for the super admin orders page. */
    @Query(value = "select o, c.name, c.email, p.name" + FILTER + " and (:status is null or o.status = :status)",
            countQuery = "select count(o)" + FILTER + " and (:status is null or o.status = :status)")
    Page<Object[]> searchWithNames(String keyword, java.time.Instant from, java.time.Instant to, EOrderStatus status,
                                   Pageable pageable);

    /** Uses per coupon for a page of coupons in one query: rows of [couponId, count]. */
    @Query("select o.couponId, count(o) from PlanOrder o where o.couponId in :couponIds and o.status in :statuses"
            + " group by o.couponId")
    java.util.List<Object[]> countUsesByCoupon(Collection<Long> couponIds, Collection<EOrderStatus> statuses);

    /** Usage history of one coupon: rows of [PlanOrder, company name, company email], newest first. */
    @Query("select o, c.name, c.email from PlanOrder o join Company c on c.id = o.companyId"
            + " where o.couponId = :couponId order by o.id desc")
    java.util.List<Object[]> usageOfCoupon(Long couponId);

    @Query("select o.status, count(o)" + FILTER + " group by o.status")
    java.util.List<Object[]> countByStatus(String keyword, java.time.Instant from, java.time.Instant to);

    // ---- super admin dashboard (docs/analysis/06 §3.1)
    java.util.List<PlanOrder> findByStatus(EOrderStatus status);

    long countByStatus(EOrderStatus status);
}
