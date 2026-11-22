package com.terp.erp.support.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    Optional<ContactMessage> findByIdAndDeletedFalse(Long id);

    @Query("select m from ContactMessage m where m.companyId = :company and m.deleted = false"
            + " and (:kw is null or lower(m.name) like concat('%', :kw, '%')"
            + " or lower(m.email) like concat('%', :kw, '%') or lower(m.subject) like concat('%', :kw, '%'))")
    Page<ContactMessage> search(@Param("company") Long companyId, @Param("kw") String keyword, Pageable pageable);
}
