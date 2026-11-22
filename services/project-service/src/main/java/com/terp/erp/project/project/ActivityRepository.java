package com.terp.erp.project.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Page<Activity> findByProjectIdOrderByIdDesc(Long projectId, Pageable pageable);

    List<Activity> findByProjectIdOrderByIdAsc(Long projectId);
}
