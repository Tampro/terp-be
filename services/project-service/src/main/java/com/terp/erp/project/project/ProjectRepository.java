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
}
