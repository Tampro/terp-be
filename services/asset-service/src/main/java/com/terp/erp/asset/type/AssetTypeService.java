package com.terp.erp.asset.type;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.asset.common.EAssetError;
import com.terp.erp.asset.common.Texts;
import com.terp.erp.asset.item.AssetRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Asset Types page (docs/analysis/06 §8.2.4): name unique in the company (AST_003); a type with assets cannot be
 * deleted (AST_004).
 */
@Service
@RequiredArgsConstructor
public class AssetTypeService {
    private final AssetTypeRepository types;
    private final AssetRepository assets;

    public record TypeForm(
            @Schema(example = "Computer Hardware", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Schema(example = "Laptops, desktops and monitors") @Size(max = 1000) String description) {
    }

    /** assets = the Assets column (not deleted). */
    public record TypeDto(String id, String name, String description, long assets, Instant createdDate) {
    }

    public record Option(String id, String name) {
    }

    @Transactional(readOnly = true)
    public Page<TypeDto> search(Long requestedCompanyId, String keyword, Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<AssetType> page = types.search(companyId, Texts.kw(keyword), pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] r : assets.countByType(companyId, page.map(AssetType::getId).toList())) {
                counts.put((Long) r[0], (Long) r[1]);
            }
        }
        return page.map(t -> dto(t, counts.getOrDefault(t.getId(), 0L)));
    }

    /** The Asset Type dropdown of the asset form and filters. */
    @Transactional(readOnly = true)
    public List<Option> options(Long requestedCompanyId) {
        return types.findByCompanyIdAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId)).stream()
                .map(t -> new Option(t.getId().toString(), t.getName())).toList();
    }

    @Transactional(readOnly = true)
    public TypeDto get(Long id) {
        AssetType t = require(id);
        return dto(t, assets.countByAssetTypeIdAndDeletedFalse(t.getId()));
    }

    @Transactional
    public TypeDto create(Long requestedCompanyId, TypeForm form) {
        AssetType t = new AssetType();
        t.setCompanyId(Texts.company(requestedCompanyId));
        apply(t, form);
        return dto(types.save(t), 0);
    }

    /** Full replacement. */
    @Transactional
    public TypeDto update(Long id, TypeForm form) {
        AssetType t = require(id);
        apply(t, form);
        return dto(t, assets.countByAssetTypeIdAndDeletedFalse(t.getId()));
    }

    @Transactional
    public void delete(Long id) {
        AssetType t = require(id);
        if (assets.countByAssetTypeIdAndDeletedFalse(t.getId()) > 0) {
            throw ApiException.badRequest(EAssetError.AST_004);
        }
        t.setDeleted(true);
    }

    private void apply(AssetType t, TypeForm f) {
        String name = f.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(t.getCompanyId(), name,
                t.getId() == null ? -1L : t.getId())) {
            throw ApiException.badRequest(EAssetError.AST_003);
        }
        t.setName(name);
        t.setDescription(Texts.blankToNull(f.description()));
    }

    private static TypeDto dto(AssetType t, long count) {
        return new TypeDto(t.getId().toString(), t.getName(), t.getDescription(), count, t.getCreatedDate());
    }

    /** A type of the caller's company (AST_002 otherwise, 404 on direct access). */
    public AssetType require(Long id) {
        AssetType t = types.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EAssetError.AST_002));
        TenantGuard.assertSameCompany(t.getCompanyId(), EAssetError.AST_002);
        return t;
    }
}
