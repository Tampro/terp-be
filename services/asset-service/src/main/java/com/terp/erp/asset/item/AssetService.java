package com.terp.erp.asset.item;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.asset.assignment.AssetAssignmentService.AssignmentDto;
import com.terp.erp.asset.assignment.CurrentAssignments;
import com.terp.erp.asset.client.MediaClient;
import com.terp.erp.asset.client.MediaClient.MediaFile;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.item.Asset.ECondition;
import com.terp.erp.asset.item.Asset.EDepreciationMethod;
import com.terp.erp.asset.item.Asset.EStatus;
import com.terp.erp.asset.type.AssetType;
import com.terp.erp.asset.type.AssetTypeRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assets page (docs/analysis/06 §8.2.2): add, edit, delete, list with filters and status tabs. Asset code unique in
 * the company (AST_006); image and documents are media library files of the company (AST_009). The form may only
 * choose AVAILABLE or DISPOSED (AST_015); ASSIGNED and MAINTENANCE come from assignments and maintenance and must be
 * ended first (AST_016).
 */
@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assets;
    private final AssetTypeRepository types;
    private final MediaClient media;
    private final CurrentAssignments current;

    public record AssetForm(
            @Schema(example = "Dell Latitude 5440", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String name,
            @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long assetTypeId,
            @Size(max = 100) String serialNumber,
            @Schema(example = "AST-0001", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50)
            String assetCode,
            LocalDate purchaseDate,
            @Schema(description = "Company currency", example = "25000000") @DecimalMin("0")
            @Digits(integer = 13, fraction = 2) BigDecimal purchaseCost,
            @Schema(description = "AVAILABLE or DISPOSED; omitted = AVAILABLE on create, kept on edit") EStatus status,
            @Schema(description = "Omitted = GOOD on create, kept on edit") ECondition condition,
            @Size(max = 2000) String description,
            @Size(max = 200) String location,
            @Size(max = 200) String supplier,
            @Size(max = 1000) String warrantyInfo,
            LocalDate warrantyExpiryDate,
            @Schema(description = "Media library file id of the image") Long imageFileId,
            @Schema(description = "Media library file ids (invoices, manuals, warranty); full set") Set<Long>
            documentFileIds,
            @Schema(description = "Omitted = STRAIGHT_LINE") EDepreciationMethod depreciationMethod,
            @Schema(example = "4") @Min(0) @Max(100) Integer usefulLifeYears,
            @Schema(example = "2000000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal salvageValue) {
    }

    public record TypeRef(String id, String name) {
    }

    public record FileRef(String id, String name, String mimeType, String viewUrl, String downloadUrl) {
    }

    /**
     * image and documents only on the details (links from media-service); currentAssignment = the Assigned To column
     * and the Currently Assigned panel (null when not assigned).
     */
    public record AssetDto(String id, String name, TypeRef assetType, String assetCode, String serialNumber,
                           LocalDate purchaseDate, BigDecimal purchaseCost, EStatus status, ECondition condition,
                           String description, String location, String supplier, String warrantyInfo,
                           LocalDate warrantyExpiryDate, EDepreciationMethod depreciationMethod, int usefulLifeYears,
                           int usefulLifeMonths, BigDecimal salvageValue, String imageFileId, FileRef image,
                           List<String> documentFileIds, List<FileRef> documents, AssignmentDto currentAssignment,
                           Instant createdDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record Filter(Long assetTypeId, ECondition condition, LocalDate purchaseFrom, LocalDate purchaseTo,
                         String keyword) {
    }

    @Transactional(readOnly = true)
    public Page<AssetDto> search(Long requestedCompanyId, Filter f, EStatus status, Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<Asset> page = assets.search(companyId, f.assetTypeId(), f.condition(), f.purchaseFrom(), f.purchaseTo(),
                status, Texts.kw(f.keyword()), pageable);
        Map<Long, AssetType> byType = typesOf(page.map(Asset::getAssetTypeId).toSet());
        Map<Long, AssignmentDto> holders = current.of(page.stream().filter(a -> a.getStatus() == EStatus.ASSIGNED)
                .map(Asset::getId).toList(), companyId);
        return page.map(a -> dto(a, byType.get(a.getAssetTypeId()), false, holders.get(a.getId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, Filter f) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : assets.countByStatus(Texts.company(requestedCompanyId), f.assetTypeId(), f.condition(),
                f.purchaseFrom(), f.purchaseTo(), Texts.kw(f.keyword()))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public AssetDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public AssetDto create(Long requestedCompanyId, AssetForm form) {
        Asset a = new Asset();
        a.setCompanyId(Texts.company(requestedCompanyId));
        apply(a, form);
        return detail(assets.save(a));
    }

    /** Full replacement; status and condition omitted = kept. */
    @Transactional
    public AssetDto update(Long id, AssetForm form) {
        Asset a = require(id);
        apply(a, form);
        return detail(a);
    }

    /** An assigned asset or one under maintenance must be returned / finished first (AST_016). */
    @Transactional
    public void delete(Long id) {
        Asset a = require(id);
        if (a.getStatus() == EStatus.ASSIGNED || a.getStatus() == EStatus.MAINTENANCE) {
            throw ApiException.badRequest(EAssetError.AST_016);
        }
        a.setDeleted(true);
    }

    /** Shared by the form and the import: checks the type, the code, the money and the files. */
    void apply(Asset a, AssetForm f) {
        // A field of the form, not the resource of the URL: 400, like a company's own missing type.
        AssetType type = types.findByIdAndDeletedFalse(f.assetTypeId())
                .filter(t -> t.getCompanyId().equals(a.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EAssetError.AST_002));
        String code = f.assetCode().trim();
        if (assets.existsByCompanyIdAndAssetCodeIgnoreCaseAndDeletedFalseAndIdNot(a.getCompanyId(), code,
                a.getId() == null ? -1L : a.getId())) {
            throw ApiException.badRequest(EAssetError.AST_006);
        }
        if (f.status() != null && f.status() != a.getStatus()) {
            if (f.status() != EStatus.AVAILABLE && f.status() != EStatus.DISPOSED) {
                throw ApiException.badRequest(EAssetError.AST_015);
            }
            if (a.getStatus() == EStatus.ASSIGNED || a.getStatus() == EStatus.MAINTENANCE) {
                throw ApiException.badRequest(EAssetError.AST_016);
            }
        }
        BigDecimal cost = f.purchaseCost() == null ? BigDecimal.ZERO : f.purchaseCost();
        BigDecimal salvage = f.salvageValue() == null ? BigDecimal.ZERO : f.salvageValue();
        if (salvage.compareTo(cost) > 0) {
            throw ApiException.badRequest(EAssetError.AST_010);
        }
        if (f.warrantyExpiryDate() != null && f.purchaseDate() != null
                && f.warrantyExpiryDate().isBefore(f.purchaseDate())) {
            throw ApiException.badRequest(EAssetError.AST_017);
        }
        // Only newly attached files are checked: one already on the asset stays even if its media row changed.
        if (f.imageFileId() != null && !Objects.equals(f.imageFileId(), a.getImageFileId())) {
            media.require(f.imageFileId(), a.getCompanyId());
        }
        Set<Long> docs = f.documentFileIds() == null ? Set.of() : new LinkedHashSet<>(f.documentFileIds());
        for (Long d : docs) {
            if (!a.getDocumentFileIds().contains(d)) {
                media.require(d, a.getCompanyId());
            }
        }
        a.setName(f.name().trim());
        a.setAssetTypeId(type.getId());
        a.setSerialNumber(Texts.blankToNull(f.serialNumber()));
        a.setAssetCode(code);
        a.setPurchaseDate(f.purchaseDate());
        a.setPurchaseCost(cost);
        if (f.status() != null) {
            a.setStatus(f.status());
        }
        if (f.condition() != null) {
            a.setCondition(f.condition());
        }
        a.setDescription(Texts.blankToNull(f.description()));
        a.setLocation(Texts.blankToNull(f.location()));
        a.setSupplier(Texts.blankToNull(f.supplier()));
        a.setWarrantyInfo(Texts.blankToNull(f.warrantyInfo()));
        a.setWarrantyExpiryDate(f.warrantyExpiryDate());
        a.setImageFileId(f.imageFileId());
        a.getDocumentFileIds().clear();
        a.getDocumentFileIds().addAll(docs);
        a.setDepreciationMethod(f.depreciationMethod() == null ? EDepreciationMethod.STRAIGHT_LINE
                : f.depreciationMethod());
        a.setUsefulLifeYears(f.usefulLifeYears() == null ? 0 : f.usefulLifeYears());
        a.setSalvageValue(salvage);
    }

    private AssetDto detail(Asset a) {
        AssignmentDto holder = a.getStatus() != EStatus.ASSIGNED ? null
                : current.of(List.of(a.getId()), a.getCompanyId()).get(a.getId());
        return dto(a, types.findById(a.getAssetTypeId()).orElse(null), true, holder);
    }

    private Map<Long, AssetType> typesOf(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : types.findAllById(ids).stream()
                .collect(Collectors.toMap(AssetType::getId, Function.identity()));
    }

    private AssetDto dto(Asset a, AssetType t, boolean withFiles, AssignmentDto holder) {
        FileRef image = withFiles ? file(a.getImageFileId()) : null;
        List<FileRef> docs = withFiles ? a.getDocumentFileIds().stream().map(this::file).filter(Objects::nonNull)
                .toList() : null;
        return new AssetDto(a.getId().toString(), a.getName(),
                t == null ? null : new TypeRef(t.getId().toString(), t.getName()), a.getAssetCode(),
                a.getSerialNumber(), a.getPurchaseDate(), a.getPurchaseCost(), a.getStatus(), a.getCondition(),
                a.getDescription(), a.getLocation(), a.getSupplier(), a.getWarrantyInfo(), a.getWarrantyExpiryDate(),
                a.getDepreciationMethod(), a.getUsefulLifeYears(), a.getUsefulLifeYears() * 12, a.getSalvageValue(),
                a.getImageFileId() == null ? null : a.getImageFileId().toString(), image,
                a.getDocumentFileIds().stream().map(String::valueOf).toList(), docs, holder, a.getCreatedDate());
    }

    /** A file the caller cannot see (or a deleted one) is simply not linked. */
    private FileRef file(Long id) {
        MediaFile f = media.find(id);
        return f == null ? null : new FileRef(f.id(), f.name(), f.mimeType(), f.viewUrl(), f.downloadUrl());
    }

    public Asset require(Long id) {
        Asset a = assets.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EAssetError.AST_005));
        TenantGuard.assertSameCompany(a.getCompanyId(), EAssetError.AST_005);
        return a;
    }
}
