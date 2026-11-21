package com.terp.erp.iam.helpdesk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, Long> {
    Optional<HelpdeskTicket> findByIdAndDeletedFalse(Long id);

    /** companyId null = every company (super admin only; the service decides). keyword is already lower-case "%x%". */
    @Query("""
            select t from HelpdeskTicket t
            where t.deleted = false
              and (:companyId is null or t.companyId = :companyId)
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:categoryId is null or t.categoryId = :categoryId)
              and (:keyword is null or lower(t.title) like :keyword or t.ticketNumber like :keyword)""")
    Page<HelpdeskTicket> search(@Param("companyId") Long companyId, @Param("status") EHelpdeskStatus status,
                                @Param("priority") EHelpdeskPriority priority, @Param("categoryId") Long categoryId,
                                @Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select t.status, count(t) from HelpdeskTicket t
            where t.deleted = false and (:companyId is null or t.companyId = :companyId)
            group by t.status""")
    List<Object[]> countByStatus(@Param("companyId") Long companyId);

    @Query("""
            select extract(month from t.createdDate), count(t) from HelpdeskTicket t
            where t.deleted = false and t.createdDate >= :from and t.createdDate < :to
            group by extract(month from t.createdDate)""")
    List<Object[]> createdPerMonth(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select extract(month from t.resolvedAt), count(t) from HelpdeskTicket t
            where t.deleted = false and t.resolvedAt >= :from and t.resolvedAt < :to
            group by extract(month from t.resolvedAt)""")
    List<Object[]> resolvedPerMonth(@Param("from") Instant from, @Param("to") Instant to);
}
