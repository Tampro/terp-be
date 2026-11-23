package com.terp.erp.accounting.vendor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    /** keyword on code, company name, contact person, email and tax number; the caller escapes with !. */
    @Query("select c from Vendor c where c.companyId = :companyId and c.deleted = false and (:kw = '' "
            + "or lower(c.vendorCode) like :kw escape '!' or lower(c.companyName) like :kw escape '!' "
            + "or lower(c.contactPerson) like :kw escape '!' or lower(c.email) like :kw escape '!' "
            + "or lower(c.taxNumber) like :kw escape '!')")
    Page<Vendor> search(Long companyId, String kw, Pageable pageable);

    Optional<Vendor> findByIdAndDeletedFalse(Long id);

    /** The party a portal user is linked to; a user is linked to at most one (ACC_003 / create checks). */
    Optional<Vendor> findFirstByCompanyIdAndUserIdAndDeletedFalse(Long companyId, Long userId);

    boolean existsByCompanyIdAndUserIdAndDeletedFalseAndIdNot(Long companyId, Long userId, Long id);

    long countByCompanyIdAndDeletedFalse(Long companyId);
}
