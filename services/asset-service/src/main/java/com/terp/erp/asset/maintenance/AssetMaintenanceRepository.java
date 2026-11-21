package com.terp.erp.asset.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, Long> {
    Page<AssetMaintenance> findByAssetIdOrderByStartDateDescIdDesc(Long assetId, Pageable pageable);

    Optional<AssetMaintenance> findByIdAndAssetId(Long id, Long assetId);

    /** Upcoming Maintenance of the dashboard: open records starting from a date, soonest first. */
    List<AssetMaintenance> findTop5ByCompanyIdAndStatusInAndStartDateGreaterThanEqualOrderByStartDateAsc(
            Long companyId, Collection<AssetMaintenance.EStatus> statuses, LocalDate from);

    boolean existsByAssetIdAndStatusAndIdNot(Long assetId, AssetMaintenance.EStatus status, Long id);
}
