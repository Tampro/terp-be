package com.terp.erp.asset.assignment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {
    Optional<AssetAssignment> findFirstByAssetIdAndStatus(Long assetId, AssetAssignment.EStatus status);

    /** The open assignment of each asset of a page (Assigned To column). */
    List<AssetAssignment> findByAssetIdInAndStatus(Collection<Long> assetIds, AssetAssignment.EStatus status);

    Page<AssetAssignment> findByAssetIdOrderByCheckoutDateDescIdDesc(Long assetId, Pageable pageable);

    /** Recent Assignments of the dashboard. */
    List<AssetAssignment> findTop5ByCompanyIdOrderByCheckoutDateDescIdDesc(Long companyId);
}
