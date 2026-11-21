package com.terp.erp.asset.type;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {
    /** keyword on the name; the caller escapes with !. */
    @Query("select t from AssetType t where t.companyId = :companyId and t.deleted = false "
            + "and (:kw = '' or lower(t.name) like :kw escape '!')")
    Page<AssetType> search(Long companyId, String kw, Pageable pageable);

    Optional<AssetType> findByIdAndDeletedFalse(Long id);

    List<AssetType> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);

    Optional<AssetType> findFirstByCompanyIdAndNameIgnoreCaseAndDeletedFalse(Long companyId, String name);
}
