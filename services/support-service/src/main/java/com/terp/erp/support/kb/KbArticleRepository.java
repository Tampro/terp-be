package com.terp.erp.support.kb;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {
    Optional<KbArticle> findByIdAndDeletedFalse(Long id);

    boolean existsByCategoryIdAndDeletedFalse(Long categoryId);

    /** published null = both (admin); the portal passes true. */
    @Query("select a from KbArticle a where a.companyId = :company and a.deleted = false"
            + " and (:published is null or a.published = :published)"
            + " and (:category is null or a.categoryId = :category)"
            + " and (:kw is null or lower(a.title) like concat('%', :kw, '%')"
            + " or lower(a.description) like concat('%', :kw, '%'))")
    Page<KbArticle> search(@Param("company") Long companyId, @Param("published") Boolean published,
                           @Param("category") Long categoryId, @Param("kw") String keyword, Pageable pageable);
}
