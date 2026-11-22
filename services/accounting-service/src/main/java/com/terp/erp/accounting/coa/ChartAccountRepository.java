package com.terp.erp.accounting.coa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChartAccountRepository extends JpaRepository<ChartAccount, Long> {
    /** keyword on code and name; null filters = all; the caller escapes with !. */
    @Query("select a from ChartAccount a where a.companyId = :companyId "
            + "and (:typeId is null or a.accountTypeId = :typeId) "
            + "and (:normalBalance is null or a.normalBalance = :normalBalance) "
            + "and (:active is null or a.active = :active) and (:kw = '' "
            + "or lower(a.code) like :kw escape '!' or lower(a.name) like :kw escape '!')")
    Page<ChartAccount> search(Long companyId, Long typeId, ENormalBalance normalBalance, Boolean active, String kw,
                              Pageable pageable);

    List<ChartAccount> findByCompanyIdOrderByCode(Long companyId);

    Optional<ChartAccount> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCodeAndIdNot(Long companyId, String code, Long id);

    boolean existsByParentId(Long parentId);

    boolean existsByAccountTypeId(Long accountTypeId);

    /** SELECT ... FOR UPDATE: concurrent postings on one account wait here, so no balance change is lost. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChartAccount a where a.id = :id")
    Optional<ChartAccount> lockById(Long id);
}
