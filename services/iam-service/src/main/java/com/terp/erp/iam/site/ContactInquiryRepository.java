package com.terp.erp.iam.site;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
    @Query("select c from ContactInquiry c where (:status is null or c.status = :status) and (:keyword = ''"
            + " or lower(c.name) like lower(concat('%', :keyword, '%'))"
            + " or lower(c.email) like lower(concat('%', :keyword, '%'))"
            + " or lower(c.subject) like lower(concat('%', :keyword, '%')))")
    Page<ContactInquiry> search(String keyword, ContactInquiry.EStatus status, Pageable pageable);
}
