package com.terp.erp.project.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByIdAndDeletedFalse(Long id);

    List<Project> findByIdIn(Collection<Long> ids);

    @Query("select p from Project p where p.companyId = :company and p.deleted = false"
            + " and (:status is null or p.status = :status)"
            + " and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))")
    Page<Project> search(@Param("company") Long company, @Param("status") EProjectStatus status,
                         @Param("q") String q, Pageable pageable);

    /** {@link #search} limited to projects I am on the team of or created (PROJECT_VIEW_OWN). */
    @Query("select p from Project p where p.companyId = :company and p.deleted = false"
            + " and (:me member of p.userIds or p.createdBy = :sub)"
            + " and (:status is null or p.status = :status)"
            + " and (:q is null or lower(p.name) like lower(concat('%', :q, '%')))")
    Page<Project> searchOwn(@Param("company") Long company, @Param("status") EProjectStatus status,
                            @Param("q") String q, @Param("me") Long me, @Param("sub") String sub, Pageable pageable);

    /** Live projects with this accounting customer among their clients (client portal). */
    @Query("select p from Project p where p.companyId = :company and p.deleted = false"
            + " and :customer member of p.clientIds")
    Page<Project> findOfClient(@Param("company") Long company, @Param("customer") Long customerId, Pageable pageable);

    /** Every live project of the company (dashboard stats). */
    List<Project> findByCompanyIdAndDeletedFalse(Long companyId);

    /** Live projects I am on the team of or created (PROJECT_VIEW_OWN dashboard stats). */
    @Query("select p from Project p where p.companyId = :company and p.deleted = false"
            + " and (:me member of p.userIds or p.createdBy = :sub)")
    List<Project> listOwn(@Param("company") Long company, @Param("me") Long me, @Param("sub") String sub);
}
