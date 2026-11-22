package com.terp.erp.support.kb;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    Optional<Faq> findByIdAndDeletedFalse(Long id);

    @Query("select f from Faq f where f.companyId = :company and f.deleted = false"
            + " and (:published is null or f.published = :published)"
            + " and (:kw is null or lower(f.title) like concat('%', :kw, '%'))")
    Page<Faq> search(@Param("company") Long companyId, @Param("published") Boolean published,
                     @Param("kw") String keyword, Pageable pageable);
}
