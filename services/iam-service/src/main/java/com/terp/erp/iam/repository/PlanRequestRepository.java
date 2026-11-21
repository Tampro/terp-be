package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.model.PlanRequest;
import com.terp.erp.iam.saas.EPlanRequestStatus;

import java.util.List;

public interface PlanRequestRepository extends JpaRepository<PlanRequest, Long> {
    Page<PlanRequest> findByCompanyId(Long companyId, Pageable pageable);

    boolean existsByCompanyIdAndStatus(Long companyId, EPlanRequestStatus status);

    /** Keyword matches company name, code or email, or the plan name. */
    String FILTER = " from PlanRequest r join Company c on c.id = r.companyId join Plan p on p.id = r.planId"
            + " where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + "   or lower(c.code) like lower(concat('%', :keyword, '%'))"
            + "   or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%'))"
            + "   or lower(p.name) like lower(concat('%', :keyword, '%')))";

    /** Rows of [PlanRequest, company name, company email, plan name] — one query for the admin list. */
    @Query(value = "select r, c.name, c.email, p.name" + FILTER + " and (:status is null or r.status = :status)",
            countQuery = "select count(r)" + FILTER + " and (:status is null or r.status = :status)")
    Page<Object[]> searchWithNames(String keyword, EPlanRequestStatus status, Pageable pageable);

    @Query("select r.status, count(r)" + FILTER + " group by r.status")
    List<Object[]> countByStatus(String keyword);

    /** Super admin dashboard: Pending Requests. */
    long countByStatus(EPlanRequestStatus status);
}
