package com.terp.erp.document.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.document.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Long> {
    /** keyword on name and description; the caller escapes with !. */
    String FILTERS = "from ContractType t where t.companyId = :companyId and t.deleted = false "
            + "and (:renewable is null or t.renewable = :renewable) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.description) like :kw escape '!')";

    @Query(value = "select t " + FILTERS + " and (:status is null or t.status = :status)",
            countQuery = "select count(t) " + FILTERS + " and (:status is null or t.status = :status)")
    Page<ContractType> search(Long companyId, Boolean renewable, EStatus status, String kw, Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, Boolean renewable, String kw);

    Optional<ContractType> findByIdAndDeletedFalse(Long id);

    List<ContractType> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
