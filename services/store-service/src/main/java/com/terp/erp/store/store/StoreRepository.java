package com.terp.erp.store.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/** Every query filters deleted = false: deleted stores only keep the history. */
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByIdAndDeletedFalse(Long id);

    /** kw = lower-cased keyword on code or name, null = no filter; active null = both. */
    @Query("select s from Store s where s.companyId = :companyId and s.deleted = false "
            + "and (:kw is null or lower(s.code) like concat('%', :kw, '%') or lower(s.name) like concat('%', :kw, '%')) "
            + "and (:active is null or s.active = :active)")
    Page<Store> search(Long companyId, String kw, Boolean active, Pageable pageable);

    boolean existsByCompanyIdAndCodeAndDeletedFalseAndIdNot(Long companyId, String code, Long id);

    Optional<Store> findFirstByCompanyIdAndWarehouseIdAndDeletedFalse(Long companyId, Long warehouseId);
}
