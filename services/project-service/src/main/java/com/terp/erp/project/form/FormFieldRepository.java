package com.terp.erp.project.form;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FormFieldRepository extends JpaRepository<FormField, Long> {
    List<FormField> findByFormIdOrderByPositionAsc(Long formId);

    /** Bulk delete runs at once, so the re-inserted fields (same keys) do not hit uk_form_field_key. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FormField f where f.formId = :formId")
    void deleteAllOfForm(@Param("formId") Long formId);
}
