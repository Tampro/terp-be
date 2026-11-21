package com.terp.erp.iam.email;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    Optional<EmailTemplate> findByCode(String code);

    /** Search by template name or sender name (docs/analysis/06 §3.3.1). */
    @Query("select t from EmailTemplate t where :keyword = ''"
            + " or lower(t.name) like lower(concat('%', :keyword, '%'))"
            + " or lower(coalesce(t.fromName, '')) like lower(concat('%', :keyword, '%'))")
    Page<EmailTemplate> search(String keyword, Pageable pageable);
}
