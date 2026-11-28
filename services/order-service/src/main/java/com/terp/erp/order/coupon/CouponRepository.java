package com.terp.erp.order.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByIdAndDeletedFalse(Long id);

    Optional<Coupon> findByCompanyIdAndCodeAndDeletedFalse(Long companyId, String code);

    /** The live coupon of this code, locked until the order's transaction ends (no over-use under concurrency). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.companyId = :company and c.code = :code and c.deleted = false")
    Optional<Coupon> lockLive(@Param("company") Long companyId, @Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> lockById(@Param("id") Long id);

    @Query("""
            select c from Coupon c where c.companyId = :company and c.deleted = false
              and (:active is null or c.active = :active)
              and (:q is null or c.code like concat('%', :q, '%') or c.name like concat('%', :q, '%'))""")
    Page<Coupon> search(@Param("company") Long companyId, @Param("active") Boolean active, @Param("q") String q,
                        Pageable pageable);
}
