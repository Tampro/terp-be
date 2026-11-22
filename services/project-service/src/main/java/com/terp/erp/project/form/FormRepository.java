package com.terp.erp.project.form;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FormRepository extends JpaRepository<Form, Long> {
    Optional<Form> findByIdAndDeletedFalse(Long id);

    Optional<Form> findByPublicTokenAndDeletedFalse(String publicToken);

    @Query("select f from Form f where f.companyId = :companyId and f.deleted = false"
            + " and (:kw is null or lower(f.name) like lower(concat('%', :kw, '%')))")
    Page<Form> search(@Param("companyId") Long companyId, @Param("kw") String keyword, Pageable pageable);
}
