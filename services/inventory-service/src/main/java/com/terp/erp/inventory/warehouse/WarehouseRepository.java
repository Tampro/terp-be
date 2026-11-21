package com.terp.erp.inventory.warehouse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    /** keyword on name or city; the caller escapes with !. */
    @Query("select w from Warehouse w where w.companyId = :companyId and w.deleted = false "
            + "and (:kw = '' or lower(w.name) like :kw escape '!' or lower(w.city) like :kw escape '!') "
            + "and (:city = '' or lower(w.city) = :city) "
            + "and (:active is null or w.active = :active)")
    Page<Warehouse> search(Long companyId, String kw, String city, Boolean active, Pageable pageable);

    Optional<Warehouse> findByIdAndDeletedFalse(Long id);
}
