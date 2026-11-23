package com.terp.erp.crm.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByIdAndDeletedFalse(Long id);

    /** Row lock for signing: two sides signing at once are serialised, so the second sees the first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> lockById(@Param("id") Long id);

    boolean existsByContractTypeIdAndDeletedFalse(Long contractTypeId);

    boolean existsByCompanyIdAndNumber(Long companyId, String number);

    @Query("""
            select c from Contract c where c.companyId = :companyId and c.deleted = false
              and (:customerId is null or c.customerId = :customerId)
              and (:typeId is null or c.contractTypeId = :typeId)
              and (:status is null or c.status = :status)
              and (:startFrom is null or c.startDate >= :startFrom) and (:startTo is null or c.startDate <= :startTo)
              and (:endFrom is null or c.endDate >= :endFrom) and (:endTo is null or c.endDate <= :endTo)
              and (:kw is null or lower(c.number) like :kw or lower(c.subject) like :kw
                   or lower(c.customerName) like :kw)""")
    Page<Contract> search(@Param("companyId") Long companyId, @Param("customerId") Long customerId,
                          @Param("typeId") Long contractTypeId, @Param("status") Contract.EStatus status,
                          @Param("startFrom") LocalDate startFrom, @Param("startTo") LocalDate startTo,
                          @Param("endFrom") LocalDate endFrom, @Param("endTo") LocalDate endTo,
                          @Param("kw") String keyword, Pageable pageable);

    /** {@link #search} limited to contracts I created (CRM_CONTRACT_VIEW_OWN). */
    @Query("""
            select c from Contract c where c.companyId = :companyId and c.deleted = false and c.createdBy = :sub
              and (:customerId is null or c.customerId = :customerId)
              and (:typeId is null or c.contractTypeId = :typeId)
              and (:status is null or c.status = :status)
              and (:startFrom is null or c.startDate >= :startFrom) and (:startTo is null or c.startDate <= :startTo)
              and (:endFrom is null or c.endDate >= :endFrom) and (:endTo is null or c.endDate <= :endTo)
              and (:kw is null or lower(c.number) like :kw or lower(c.subject) like :kw
                   or lower(c.customerName) like :kw)""")
    Page<Contract> searchOwn(@Param("companyId") Long companyId, @Param("customerId") Long customerId,
                             @Param("typeId") Long contractTypeId, @Param("status") Contract.EStatus status,
                             @Param("startFrom") LocalDate startFrom, @Param("startTo") LocalDate startTo,
                             @Param("endFrom") LocalDate endFrom, @Param("endTo") LocalDate endTo,
                             @Param("kw") String keyword, @Param("sub") String sub, Pageable pageable);

    @Query("select c.status, count(c) from Contract c where c.companyId = :companyId and c.deleted = false "
            + "and c.createdBy = :sub group by c.status")
    List<Object[]> countByStatusOwn(@Param("companyId") Long companyId, @Param("sub") String sub);

    /** Contracts of one accounting customer (client portal). */
    Page<Contract> findByCompanyIdAndCustomerIdAndDeletedFalse(Long companyId, Long customerId, Pageable pageable);

    @Query("select c.status, count(c) from Contract c where c.companyId = :companyId and c.deleted = false group by c.status")
    List<Object[]> countByStatus(@Param("companyId") Long companyId);
}
