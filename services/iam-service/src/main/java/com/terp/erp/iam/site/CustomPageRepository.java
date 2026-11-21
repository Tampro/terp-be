package com.terp.erp.iam.site;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomPageRepository extends JpaRepository<CustomPage, Long> {
    @Query("select p from CustomPage p where :keyword = '' or lower(p.title) like lower(concat('%', :keyword, '%'))")
    Page<CustomPage> search(String keyword, Pageable pageable);

    boolean existsBySlug(String slug);

    Optional<CustomPage> findBySlugAndPublishedTrue(String slug);

    List<CustomPage> findByPublishedTrueOrderBySortOrderAscIdAsc();
}
