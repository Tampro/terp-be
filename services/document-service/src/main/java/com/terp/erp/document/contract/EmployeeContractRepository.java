package com.terp.erp.document.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeContractRepository extends JpaRepository<EmployeeContract, Long> {
    /** keyword on contract number and the copied employee name / no. / email; the caller escapes with !. */
    String FILTERS = "from EmployeeContract c where c.companyId = :companyId and c.deleted = false "
            + "and (:contractTypeId is null or c.contractTypeId = :contractTypeId) "
            + "and (:employeeId is null or c.employeeId = :employeeId) "
            + "and (:kw = '' or lower(c.contractNumber) like :kw escape '!' or lower(c.employeeName) like :kw escape '!' "
            + "or lower(c.employeeNo) like :kw escape '!' or lower(c.employeeEmail) like :kw escape '!')";

    /** The effective status: an ACTIVE contract past its end date is EXPIRED. */
    String EFFECTIVE = "case when c.status = com.terp.erp.document.contract.EmployeeContract.EStatus.ACTIVE "
            + "and c.endDate is not null and c.endDate < :today "
            + "then com.terp.erp.document.contract.EmployeeContract.EStatus.EXPIRED else c.status end";

    @Query(value = "select c " + FILTERS + " and (:status is null or " + EFFECTIVE + " = :status)",
            countQuery = "select count(c) " + FILTERS + " and (:status is null or " + EFFECTIVE + " = :status)")
    Page<EmployeeContract> search(Long companyId, Long contractTypeId, Long employeeId,
                                  EmployeeContract.EStatus status, String kw, LocalDate today, Pageable pageable);

    @Query("select " + EFFECTIVE + ", count(c) " + FILTERS + " group by " + EFFECTIVE)
    List<Object[]> countByStatus(Long companyId, Long contractTypeId, Long employeeId, String kw, LocalDate today);

    /** Contract Near Expire: active, ending within [today, until]. */
    @Query("select count(c) from EmployeeContract c where c.companyId = :companyId and c.deleted = false "
            + "and c.status = com.terp.erp.document.contract.EmployeeContract.EStatus.ACTIVE "
            + "and c.endDate between :today and :until")
    long countNearExpire(Long companyId, LocalDate today, LocalDate until);

    /** The Contracts column of the contract types: active contracts not yet past their end. */
    @Query("select c.contractTypeId, count(c) from EmployeeContract c where c.deleted = false "
            + "and c.status = com.terp.erp.document.contract.EmployeeContract.EStatus.ACTIVE "
            + "and (c.endDate is null or c.endDate >= :today) "
            + "and c.contractTypeId in :ids group by c.contractTypeId")
    List<Object[]> countActiveByType(Collection<Long> ids, LocalDate today);

    boolean existsByContractTypeIdAndDeletedFalse(Long contractTypeId);

    Optional<EmployeeContract> findByIdAndDeletedFalse(Long id);
}
