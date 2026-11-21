package com.terp.erp.asset.maintenance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.AssetService;
import com.terp.erp.asset.maintenance.AssetMaintenance.EStatus;
import com.terp.erp.asset.maintenance.AssetMaintenance.EType;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Maintenance modal and Maintenance History (docs/analysis/06 §8.2.2, PO 2026-11-20: status follows automatically).
 * A record starting today or earlier is IN_PROGRESS and puts the asset in MAINTENANCE (it must be AVAILABLE: an
 * assigned asset is returned first, AST_023); a later start is SCHEDULED. Edit moves the status: IN_PROGRESS starts
 * it, COMPLETED / CANCELLED give the asset back (AVAILABLE).
 * TODO(assumption): a scheduled record does not start by itself on its start date (no scheduler) — it is started by
 * an edit; confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class AssetMaintenanceService {
    private final AssetMaintenanceRepository records;
    private final AssetService assets;
    private final Clock clock;

    public record MaintenanceForm(
            @Schema(example = "PREVENTIVE", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EType type,
            @Schema(description = "Omitted = today") LocalDate startDate,
            LocalDate endDate,
            @Schema(example = "500000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal cost,
            @Size(max = 2000) String details,
            @Size(max = 200) String supplier,
            @Schema(description = "Edit only; omitted = kept (create: from the start date)") EStatus status) {
    }

    public record MaintenanceDto(String id, String assetId, EType type, LocalDate startDate, LocalDate endDate,
                                 BigDecimal cost, String details, String supplier, EStatus status) {
    }

    @Transactional
    public MaintenanceDto create(Long assetId, MaintenanceForm f) {
        Asset a = assets.require(assetId);
        if (a.getStatus() == Asset.EStatus.DISPOSED) {
            throw ApiException.badRequest(EAssetError.AST_022);
        }
        AssetMaintenance m = new AssetMaintenance();
        m.setCompanyId(a.getCompanyId());
        m.setAssetId(a.getId());
        apply(m, f);
        EStatus initial = m.getStartDate().isAfter(Texts.today(clock)) ? EStatus.SCHEDULED : EStatus.IN_PROGRESS;
        move(a, m, initial);
        return dto(records.save(m));
    }

    /** Full replacement of a SCHEDULED or IN_PROGRESS record (AST_026 otherwise); status omitted = kept. */
    @Transactional
    public MaintenanceDto update(Long assetId, Long id, MaintenanceForm f) {
        Asset a = assets.require(assetId);
        AssetMaintenance m = records.findByIdAndAssetId(id, a.getId())
                .orElseThrow(() -> ApiException.notFound(EAssetError.AST_025));
        if (m.getStatus() == EStatus.COMPLETED || m.getStatus() == EStatus.CANCELLED) {
            throw ApiException.badRequest(EAssetError.AST_026);
        }
        apply(m, f);
        if (f.status() != null && f.status() != m.getStatus()) {
            move(a, m, f.status());
        }
        return dto(m);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceDto> history(Long assetId, Pageable pageable) {
        Asset a = assets.require(assetId);
        return records.findByAssetIdOrderByStartDateDescIdDesc(a.getId(), pageable).map(AssetMaintenanceService::dto);
    }

    private void apply(AssetMaintenance m, MaintenanceForm f) {
        LocalDate start = f.startDate() != null ? f.startDate()
                : m.getStartDate() != null ? m.getStartDate() : Texts.today(clock);
        if (f.endDate() != null && f.endDate().isBefore(start)) {
            throw ApiException.badRequest(EAssetError.AST_024);
        }
        m.setType(f.type());
        m.setStartDate(start);
        m.setEndDate(f.endDate());
        m.setCost(f.cost() == null ? BigDecimal.ZERO : f.cost());
        m.setDetails(Texts.blankToNull(f.details()));
        m.setSupplier(Texts.blankToNull(f.supplier()));
    }

    /** The asset follows the record: IN_PROGRESS → MAINTENANCE; leaving IN_PROGRESS → AVAILABLE. */
    private void move(Asset a, AssetMaintenance m, EStatus to) {
        if (to == EStatus.IN_PROGRESS) {
            if (a.getStatus() == Asset.EStatus.ASSIGNED) {
                throw ApiException.badRequest(EAssetError.AST_023);
            }
            if (a.getStatus() == Asset.EStatus.DISPOSED) {
                throw ApiException.badRequest(EAssetError.AST_022);
            }
            a.setStatus(Asset.EStatus.MAINTENANCE);
        } else if (m.getStatus() == EStatus.IN_PROGRESS && a.getStatus() == Asset.EStatus.MAINTENANCE
                && !records.existsByAssetIdAndStatusAndIdNot(a.getId(), EStatus.IN_PROGRESS,
                m.getId() == null ? -1L : m.getId())) {
            a.setStatus(Asset.EStatus.AVAILABLE);
        }
        m.setStatus(to);
    }

    static MaintenanceDto dto(AssetMaintenance m) {
        return new MaintenanceDto(m.getId().toString(), m.getAssetId().toString(), m.getType(), m.getStartDate(),
                m.getEndDate(), m.getCost(), m.getDetails(), m.getSupplier(), m.getStatus());
    }
}
