package com.terp.erp.inventory.catalog;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Categories, taxes and units of the item form (ERP manual 9 / 8.3). A category name is unique in the company
 * (INV_005); none of them can be deleted while an item uses it (INV_006).
 */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final CategoryRepository categories;
    private final TaxRepository taxes;
    private final UnitRepository units;
    private final ItemRepository items;

    public record CategoryForm(
            @Schema(example = "Electronics", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String name,
            // TODO(assumption): colour is optional; the TERP Core table allows null.
            @Schema(description = "#RRGGBB", example = "#1E88E5") @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
            String color) {
    }

    public record TaxForm(
            @Schema(example = "VAT 10%", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String name,
            @Schema(description = "Percent 0..100, default 0", example = "10") @DecimalMin("0") @DecimalMax("100")
            BigDecimal rate) {
    }

    public record UnitForm(
            @Schema(example = "pcs", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50)
            String name) {
    }

    public record CategoryDto(String id, String name, String color) {
    }

    public record TaxDto(String id, String name, BigDecimal rate) {
    }

    public record UnitDto(String id, String name) {
    }

    // ---- categories

    @Transactional(readOnly = true)
    public List<CategoryDto> categories(Long requestedCompanyId) {
        return categories.findByCompanyIdAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId)).stream()
                .map(CatalogService::dto).toList();
    }

    @Transactional
    public CategoryDto createCategory(Long requestedCompanyId, CategoryForm form) {
        Category c = new Category();
        c.setCompanyId(Texts.company(requestedCompanyId));
        apply(c, form);
        return dto(categories.save(c));
    }

    /** Full replacement. */
    @Transactional
    public CategoryDto updateCategory(Long id, CategoryForm form) {
        Category c = requireCategory(id);
        apply(c, form);
        return dto(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = requireCategory(id);
        if (items.existsByCategoryIdAndDeletedFalse(c.getId())) {
            throw ApiException.badRequest(EInventoryError.INV_006);
        }
        c.setDeleted(true);
    }

    private void apply(Category c, CategoryForm f) {
        String name = f.name().trim();
        if (categories.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(c.getCompanyId(), name,
                c.getId() == null ? -1L : c.getId())) {
            throw ApiException.badRequest(EInventoryError.INV_005);
        }
        c.setName(name);
        c.setColor(Texts.blankToNull(f.color()));
    }

    // ---- taxes

    @Transactional(readOnly = true)
    public List<TaxDto> taxes(Long requestedCompanyId) {
        return taxes.findByCompanyIdAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId)).stream()
                .map(CatalogService::dto).toList();
    }

    @Transactional
    public TaxDto createTax(Long requestedCompanyId, TaxForm form) {
        Tax t = new Tax();
        t.setCompanyId(Texts.company(requestedCompanyId));
        apply(t, form);
        return dto(taxes.save(t));
    }

    /** Full replacement: a missing rate becomes 0. */
    @Transactional
    public TaxDto updateTax(Long id, TaxForm form) {
        Tax t = requireTax(id);
        apply(t, form);
        return dto(t);
    }

    @Transactional
    public void deleteTax(Long id) {
        Tax t = requireTax(id);
        if (items.existsByTaxIdAndDeletedFalse(t.getId())) {
            throw ApiException.badRequest(EInventoryError.INV_006);
        }
        t.setDeleted(true);
    }

    private static void apply(Tax t, TaxForm f) {
        t.setName(f.name().trim());
        t.setRate(f.rate() == null ? BigDecimal.ZERO : f.rate());
    }

    // ---- units

    @Transactional(readOnly = true)
    public List<UnitDto> units(Long requestedCompanyId) {
        return units.findByCompanyIdAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId)).stream()
                .map(CatalogService::dto).toList();
    }

    @Transactional
    public UnitDto createUnit(Long requestedCompanyId, UnitForm form) {
        Unit u = new Unit();
        u.setCompanyId(Texts.company(requestedCompanyId));
        u.setName(form.name().trim());
        return dto(units.save(u));
    }

    /** Full replacement. */
    @Transactional
    public UnitDto updateUnit(Long id, UnitForm form) {
        Unit u = requireUnit(id);
        u.setName(form.name().trim());
        return dto(u);
    }

    @Transactional
    public void deleteUnit(Long id) {
        Unit u = requireUnit(id);
        if (items.existsByUnitIdAndDeletedFalse(u.getId())) {
            throw ApiException.badRequest(EInventoryError.INV_006);
        }
        u.setDeleted(true);
    }

    // ---- lookups

    /** By-id access: another company's row answers 404. */
    private Category requireCategory(Long id) {
        Category c = categories.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EInventoryError.INV_002));
        TenantGuard.assertSameCompany(c.getCompanyId(), EInventoryError.INV_002);
        return c;
    }

    private Tax requireTax(Long id) {
        Tax t = taxes.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EInventoryError.INV_003));
        TenantGuard.assertSameCompany(t.getCompanyId(), EInventoryError.INV_003);
        return t;
    }

    private Unit requireUnit(Long id) {
        Unit u = units.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EInventoryError.INV_004));
        TenantGuard.assertSameCompany(u.getCompanyId(), EInventoryError.INV_004);
        return u;
    }

    /** A category chosen in an item form: must be of the item's company, else 400 INV_012. */
    public Category categoryOf(Long companyId, Long id) {
        return ref(categories.findByIdAndDeletedFalse(id).filter(c -> Objects.equals(c.getCompanyId(), companyId)),
                EInventoryError.INV_012);
    }

    public Unit unitOf(Long companyId, Long id) {
        return ref(units.findByIdAndDeletedFalse(id).filter(u -> Objects.equals(u.getCompanyId(), companyId)),
                EInventoryError.INV_013);
    }

    /** null when no tax is chosen. */
    public Tax taxOf(Long companyId, Long id) {
        if (id == null) {
            return null;
        }
        return ref(taxes.findByIdAndDeletedFalse(id).filter(t -> Objects.equals(t.getCompanyId(), companyId)),
                EInventoryError.INV_014);
    }

    private static <T> T ref(Optional<T> row, ErrorCode error) {
        return row.orElseThrow(() -> ApiException.badRequest(error));
    }

    private static CategoryDto dto(Category c) {
        return new CategoryDto(c.getId().toString(), c.getName(), c.getColor());
    }

    private static TaxDto dto(Tax t) {
        return new TaxDto(t.getId().toString(), t.getName(), t.getRate());
    }

    private static UnitDto dto(Unit u) {
        return new UnitDto(u.getId().toString(), u.getName());
    }
}
