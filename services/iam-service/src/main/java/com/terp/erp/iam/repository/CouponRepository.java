package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.model.Coupon;
import com.terp.erp.iam.saas.ECouponType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByIdAndDeletedFalse(Long id);

    Optional<Coupon> findByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalse(String code);

    /** Keyword on name or code; type; created date in [from, to). */
    String FILTER = "c.deleted = false"
            + " and (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + "      or lower(c.code) like lower(concat('%', :keyword, '%')))"
            + " and (:type is null or c.type = :type)"
            + " and c.createdDate >= :from and c.createdDate < :to";

    @Query("select c from Coupon c where " + FILTER + " and (:enabled is null or c.enabled = :enabled)")
    Page<Coupon> search(String keyword, ECouponType type, Instant from, Instant to, Boolean enabled, Pageable pageable);

    /** Status tab counts: rows of [enabled, count]. */
    @Query("select c.enabled, count(c) from Coupon c where " + FILTER + " group by c.enabled")
    List<Object[]> countByEnabled(String keyword, ECouponType type, Instant from, Instant to);
}
