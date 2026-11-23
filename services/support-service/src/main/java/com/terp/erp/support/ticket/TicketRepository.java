package com.terp.erp.support.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByIdAndDeletedFalse(Long id);

    Optional<Ticket> findByCompanyIdAndTicketNumberAndDeletedFalse(Long companyId, String ticketNumber);

    Optional<Ticket> findByCompanyIdAndTrackingTokenHashAndDeletedFalse(Long companyId, String trackingTokenHash);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    @Query("select t from Ticket t where t.companyId = :company and t.deleted = false"
            + " and (:status is null or t.status = :status)"
            + " and (:category is null or t.categoryId = :category)"
            + " and (:accountType is null or t.accountType = :accountType)"
            + " and (:kw is null or lower(t.ticketNumber) like concat('%', :kw, '%')"
            + " or lower(t.subject) like concat('%', :kw, '%') or lower(t.name) like concat('%', :kw, '%')"
            + " or lower(t.email) like concat('%', :kw, '%'))")
    Page<Ticket> search(@Param("company") Long companyId, @Param("status") Ticket.EStatus status,
                        @Param("category") Long categoryId, @Param("accountType") Ticket.EAccountType accountType,
                        @Param("kw") String keyword, Pageable pageable);

    /** {@link #search} limited to my tickets: created by me or raised with my email (SUPPORT_TICKET_VIEW_OWN). */
    @Query("select t from Ticket t where t.companyId = :company and t.deleted = false"
            + " and (t.createdBy = :sub or (:email is not null and t.email = :email))"
            + " and (:status is null or t.status = :status)"
            + " and (:category is null or t.categoryId = :category)"
            + " and (:accountType is null or t.accountType = :accountType)"
            + " and (:kw is null or lower(t.ticketNumber) like concat('%', :kw, '%')"
            + " or lower(t.subject) like concat('%', :kw, '%') or lower(t.name) like concat('%', :kw, '%')"
            + " or lower(t.email) like concat('%', :kw, '%'))")
    Page<Ticket> searchOwn(@Param("company") Long companyId, @Param("status") Ticket.EStatus status,
                           @Param("category") Long categoryId, @Param("accountType") Ticket.EAccountType accountType,
                           @Param("kw") String keyword, @Param("sub") String sub, @Param("email") String email,
                           Pageable pageable);

    @Query("select t.status, count(t) from Ticket t where t.companyId = :company and t.deleted = false"
            + " and (t.createdBy = :sub or (:email is not null and t.email = :email)) group by t.status")
    List<Object[]> countByStatusOwn(@Param("company") Long companyId, @Param("sub") String sub,
                                    @Param("email") String email);

    /** [status, count] rows for the tabs, in one query. */
    @Query("select t.status, count(t) from Ticket t where t.companyId = :company and t.deleted = false"
            + " group by t.status")
    List<Object[]> countByStatus(@Param("company") Long companyId);
}
