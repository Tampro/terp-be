package com.terp.erp.asset.depreciation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.asset.assignment.AssetAssignment;
import com.terp.erp.asset.assignment.AssetAssignmentRepository;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.depreciation.DepreciationService.Summary;
import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.AssetRepository;
import com.terp.erp.asset.maintenance.AssetMaintenance;
import com.terp.erp.asset.maintenance.AssetMaintenanceRepository;
import com.terp.erp.asset.type.AssetType;
import com.terp.erp.asset.type.AssetTypeRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Asset Dashboard (docs/analysis/06 §8.2.1): status cards, value summary, distribution by type, the 5 most recent
 * assignments, the next 5 maintenance records and warranties expiring within {@value #WARRANTY_DAYS} days.
 * TODO(assumption): "nearing expiration" = within 30 days, 5 rows per list — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class AssetDashboardService {
    static final int WARRANTY_DAYS = 30;

    private final AssetRepository assets;
    private final AssetTypeRepository types;
    private final AssetAssignmentRepository assignments;
    private final AssetMaintenanceRepository maintenance;
    private final DepreciationService depreciation;
    private final Clock clock;

    public record Card(long count, BigDecimal percent) {
    }

    public record Distribution(String assetTypeId, String name, long count) {
    }

    public record RecentAssignment(String assignmentId, String assetId, String assetName, String employeeId,
                                   String employeeName, LocalDate checkoutDate, AssetAssignment.EStatus status) {
    }

    public record UpcomingMaintenance(String maintenanceId, String assetId, String assetName,
                                      AssetMaintenance.EType type, LocalDate startDate, LocalDate endDate,
                                      AssetMaintenance.EStatus status) {
    }

    public record ExpiringWarranty(String assetId, String assetName, String assetCode, LocalDate warrantyExpiryDate) {
    }

    /** total = every asset (disposed included); percents of the total. */
    public record Dashboard(long totalAssets, Card available, Card assigned, Card underMaintenance,
                            Summary valueSummary, List<Distribution> distribution,
                            List<RecentAssignment> recentAssignments, List<UpcomingMaintenance> upcomingMaintenance,
                            List<ExpiringWarranty> expiringWarranties) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long requestedCompanyId) {
        Long companyId = Texts.company(requestedCompanyId);
        LocalDate today = Texts.today(clock);
        Map<Asset.EStatus, Long> byStatus = new EnumMap<>(Asset.EStatus.class);
        for (Object[] r : assets.countByStatus(companyId, null, null, null, null, "")) {
            byStatus.put((Asset.EStatus) r[0], (Long) r[1]);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        Map<Long, String> typeNames = types.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(AssetType::getId, AssetType::getName));
        List<Distribution> dist = new ArrayList<>();
        for (Object[] r : assets.countAllByType(companyId)) {
            Long typeId = (Long) r[0];
            dist.add(new Distribution(typeId.toString(), typeNames.get(typeId), (Long) r[1]));
        }
        dist.sort(Comparator.comparingLong(Distribution::count).reversed().thenComparing(Distribution::name,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<AssetAssignment> recent = assignments.findTop5ByCompanyIdOrderByCheckoutDateDescIdDesc(companyId);
        List<AssetMaintenance> upcoming = maintenance
                .findTop5ByCompanyIdAndStatusInAndStartDateGreaterThanEqualOrderByStartDateAsc(companyId,
                        EnumSet.of(AssetMaintenance.EStatus.SCHEDULED, AssetMaintenance.EStatus.IN_PROGRESS), today);
        List<Asset> expiring = assets.findTop5ByCompanyIdAndDeletedFalseAndWarrantyExpiryDateBetweenOrderByWarrantyExpiryDateAsc(
                companyId, today, today.plusDays(WARRANTY_DAYS));
        Set<Long> ids = Stream.concat(recent.stream().map(AssetAssignment::getAssetId),
                upcoming.stream().map(AssetMaintenance::getAssetId)).collect(Collectors.toSet());
        Map<Long, String> names = assets.findAllById(ids).stream()
                .collect(Collectors.toMap(Asset::getId, Asset::getName, (a, b) -> a));
        Function<Long, String> name = id -> names.get(id);
        return new Dashboard(total, card(byStatus, Asset.EStatus.AVAILABLE, total),
                card(byStatus, Asset.EStatus.ASSIGNED, total), card(byStatus, Asset.EStatus.MAINTENANCE, total),
                DepreciationService.summary(depreciation.rows(requestedCompanyId, null, null, null)), dist,
                recent.stream().map(s -> new RecentAssignment(s.getId().toString(), s.getAssetId().toString(),
                        name.apply(s.getAssetId()), s.getEmployeeId().toString(), s.getEmployeeName(),
                        s.getCheckoutDate(), s.getStatus())).toList(),
                upcoming.stream().map(m -> new UpcomingMaintenance(m.getId().toString(), m.getAssetId().toString(),
                        name.apply(m.getAssetId()), m.getType(), m.getStartDate(), m.getEndDate(), m.getStatus()))
                        .toList(),
                expiring.stream().map(a -> new ExpiringWarranty(a.getId().toString(), a.getName(), a.getAssetCode(),
                        a.getWarrantyExpiryDate())).toList());
    }

    private static Card card(Map<Asset.EStatus, Long> by, Asset.EStatus s, long total) {
        long n = by.getOrDefault(s, 0L);
        return new Card(n, DepreciationMath.percent(BigDecimal.valueOf(n), BigDecimal.valueOf(total)));
    }
}
