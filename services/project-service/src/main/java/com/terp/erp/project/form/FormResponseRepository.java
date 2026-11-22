package com.terp.erp.project.form;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface FormResponseRepository extends JpaRepository<FormResponse, Long> {
    Optional<FormResponse> findByIdAndDeletedFalse(Long id);

    /** Row lock: two convert calls on the same response cannot both pass the "not converted yet" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FormResponse r where r.id = :id and r.deleted = false")
    Optional<FormResponse> lock(@Param("id") Long id);

    @Query("select r from FormResponse r where r.formId = :formId and r.deleted = false"
            + " and (:from is null or r.createdDate >= :from) and (:to is null or r.createdDate < :to)")
    Page<FormResponse> search(@Param("formId") Long formId, @Param("from") Instant from, @Param("to") Instant to,
                              Pageable pageable);
}
