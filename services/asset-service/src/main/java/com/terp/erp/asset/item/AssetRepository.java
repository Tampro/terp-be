package com.terp.erp.asset.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    /** keyword on name, asset code and serial number; the caller escapes with !. */
    String FILTERS = "from Asset a where a.companyId = :companyId and a.deleted = false "
            + "and (:assetTypeId is null or a.assetTypeId = :assetTypeId) "
            + "and (:condition is null or a.condition = :condition) "
            + "and (:purchaseFrom is null or a.purchaseDate >= :purchaseFrom) "
            + "and (:purchaseTo is null or a.purchaseDate <= :purchaseTo) "
            + "and (:kw = '' or lower(a.name) like :kw escape '!' or lower(a.assetCode) like :kw escape '!' "
            + "or lower(a.serialNumber) like :kw escape '!')";

    @Query(value = "select a " + FILTERS + " and (:status is null or a.status = :status)",
            countQuery = "select count(a) " + FILTERS + " and (:status is null or a.status = :status)")
    Page<Asset> search(Long companyId, Long assetTypeId, Asset.ECondition condition, LocalDate purchaseFrom,
                       LocalDate purchaseTo, Asset.EStatus status, String kw, Pageable pageable);

    @Query("select a.status, count(a) " + FILTERS + " group by a.status")
    List<Object[]> countByStatus(Long companyId, Long assetTypeId, Asset.ECondition condition, LocalDate purchaseFrom,
                                 LocalDate purchaseTo, String kw);

    @Query("select a.assetTypeId, count(a) from Asset a where a.companyId = :companyId and a.deleted = false "
            + "and a.assetTypeId in :typeIds group by a.assetTypeId")
    List<Object[]> countByType(Long companyId, Collection<Long> typeIds);

    long countByAssetTypeIdAndDeletedFalse(Long assetTypeId);

    /** Asset Distribution of the dashboard. */
    @Query("select a.assetTypeId, count(a) from Asset a where a.companyId = :companyId and a.deleted = false "
            + "group by a.assetTypeId")
    List<Object[]> countAllByType(Long companyId);

    /** Expiring Warranties: expiry within [from, to], soonest first. */
    List<Asset> findTop5ByCompanyIdAndDeletedFalseAndWarrantyExpiryDateBetweenOrderByWarrantyExpiryDateAsc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<Asset> findByIdAndDeletedFalse(Long id);

    boolean existsByCompanyIdAndAssetCodeIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String assetCode, Long id);
}
