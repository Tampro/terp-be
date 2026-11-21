package com.terp.erp.inventory.item;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.catalog.CatalogService;
import com.terp.erp.inventory.catalog.Category;
import com.terp.erp.inventory.catalog.CategoryRepository;
import com.terp.erp.inventory.catalog.Tax;
import com.terp.erp.inventory.catalog.TaxRepository;
import com.terp.erp.inventory.catalog.Unit;
import com.terp.erp.inventory.catalog.UnitRepository;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.Item.EItemType;
import com.terp.erp.inventory.stock.StockMovement;
import com.terp.erp.inventory.stock.StockService;
import com.terp.erp.inventory.stock.WarehouseStockRepository;
import com.terp.erp.inventory.warehouse.WarehouseService;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Products, services and parts (TERP Core product_service_items). SKU unique in the company among items not deleted
 * (INV_010); category, unit and tax must be of the item's company (INV_012..014).
 */
@Service
@RequiredArgsConstructor
public class ItemService {
    private static final String SKU_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SKU_LENGTH = 8;
    private static final int SKU_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ItemRepository items;
    private final CatalogService catalog;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final TaxRepository taxes;
    private final WarehouseService warehouses;
    private final StockService stock;
    private final WarehouseStockRepository stocks;

    public record ItemCreateForm(
            @Schema(example = "PRODUCT", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EItemType type,
            @Schema(example = "Wireless mouse", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(description = "Required unless generateSku = true", example = "MOUSE-01") @Size(max = 100)
            String sku,
            @Schema(description = "true: the server generates a unique 8-character uppercase SKU; sku is ignored")
            Boolean generateSku,
            @Schema(example = "1") Long taxId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long unitId,
            @Schema(example = "250000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal salePrice,
            @Schema(example = "180000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal purchasePrice,
            @Schema(example = "2.4 GHz, USB receiver") @Size(max = 500) String shortDescription,
            @Schema(example = "Long description") String description,
            @Schema(description = "PRODUCT/PART only: warehouse of the opening stock", example = "1")
            Long openingWarehouseId,
            @Schema(description = "PRODUCT/PART only: opening quantity (> 0), written as an OPENING movement",
                    example = "20") @DecimalMin(value = "0", inclusive = false) BigDecimal openingQuantity) {
    }

    public record ItemUpdateForm(
            @Schema(example = "PRODUCT", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EItemType type,
            @Schema(example = "Wireless mouse", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(example = "MOUSE-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String sku,
            @Schema(example = "1") Long taxId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long unitId,
            @Schema(example = "250000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal salePrice,
            @Schema(example = "180000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            BigDecimal purchasePrice,
            @Schema(example = "2.4 GHz, USB receiver") @Size(max = 500) String shortDescription,
            @Schema(example = "Long description") String description) {
    }

    /** quantity = stock summed over warehouses (0 for SERVICE). */
    public record ItemDto(String id, EItemType type, String name, String sku, String taxId, String taxName,
                          BigDecimal taxRate, String categoryId, String categoryName, String unitId, String unitName,
                          BigDecimal salePrice, BigDecimal purchasePrice, String shortDescription,
                          String description, BigDecimal quantity, Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<ItemDto> search(Long requestedCompanyId, String keyword, EItemType type, Long categoryId,
                                Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<Item> page = items.search(companyId, Texts.kw(keyword), type, categoryId, pageable);
        List<Item> rows = page.getContent();
        Map<Long, Category> cats = byId(categories.findAllById(ids(rows, Item::getCategoryId)), Category::getId);
        Map<Long, Unit> uns = byId(units.findAllById(ids(rows, Item::getUnitId)), Unit::getId);
        Map<Long, Tax> txs = byId(taxes.findAllById(ids(rows, Item::getTaxId)), Tax::getId);
        Map<Long, BigDecimal> qty = stock.totals(rows.stream().map(Item::getId).toList());
        return page.map(i -> dto(i, cats.get(i.getCategoryId()), uns.get(i.getUnitId()), txs.get(i.getTaxId()),
                qty.getOrDefault(i.getId(), BigDecimal.ZERO)));
    }

    @Transactional(readOnly = true)
    public ItemDto get(Long id) {
        Item i = require(id);
        return dto(i, categories.findById(i.getCategoryId()).orElse(null), units.findById(i.getUnitId()).orElse(null),
                i.getTaxId() == null ? null : taxes.findById(i.getTaxId()).orElse(null), quantity(i));
    }

    @Transactional
    public ItemDto create(Long requestedCompanyId, ItemCreateForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        boolean opening = f.openingWarehouseId() != null || f.openingQuantity() != null;
        if (opening && !f.type().stocked()) {
            throw ApiException.badRequest(EInventoryError.INV_016);
        }
        if (opening && (f.openingWarehouseId() == null || f.openingQuantity() == null)) {
            throw ApiException.badRequest(EInventoryError.INV_018);
        }
        Item i = new Item();
        i.setCompanyId(companyId);
        String sku;
        if (Boolean.TRUE.equals(f.generateSku())) {
            sku = generateSku(companyId);
        } else {
            sku = Texts.blankToNull(f.sku());
            if (sku == null) {
                throw ApiException.badRequest(EInventoryError.INV_011);
            }
        }
        Refs r = apply(i, f.type(), f.name(), sku, f.taxId(), f.categoryId(), f.unitId(), f.salePrice(),
                f.purchasePrice(), f.shortDescription(), f.description());
        // Checked before the item is written so a wrong warehouse leaves nothing behind.
        var warehouse = opening ? warehouses.activeOf(companyId, f.openingWarehouseId()) : null;
        Item saved = items.save(i);
        BigDecimal qty = BigDecimal.ZERO;
        if (opening) {
            stock.apply(companyId, List.of(new StockService.Change(saved, warehouse, f.openingQuantity())),
                    StockMovement.OPENING, saved.getId());
            qty = f.openingQuantity();
        }
        return dto(saved, r.category(), r.unit(), r.tax(), qty);
    }

    /** Full replacement (stock is changed through /stock, never here). */
    @Transactional
    public ItemDto update(Long id, ItemUpdateForm f) {
        Item i = require(id);
        BigDecimal qty = quantity(i);
        if (!f.type().stocked() && i.getType().stocked() && stocks.sumByItem(i.getId()).signum() != 0) {
            throw ApiException.badRequest(EInventoryError.INV_019);
        }
        Refs r = apply(i, f.type(), f.name(), f.sku().trim(), f.taxId(), f.categoryId(), f.unitId(), f.salePrice(),
                f.purchasePrice(), f.shortDescription(), f.description());
        return dto(i, r.category(), r.unit(), r.tax(), f.type().stocked() ? qty : BigDecimal.ZERO);
    }

    /** Soft delete. TODO(assumption): an item with stock may be deleted; its stock rows stay for the ledger. */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** By-id access: another company's item answers 404 (INV_009). */
    public Item require(Long id) {
        Item i = items.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EInventoryError.INV_009));
        TenantGuard.assertSameCompany(i.getCompanyId(), EInventoryError.INV_009);
        return i;
    }

    private record Refs(Category category, Unit unit, Tax tax) {
    }

    private Refs apply(Item i, EItemType type, String name, String sku, Long taxId, Long categoryId, Long unitId,
                       BigDecimal salePrice, BigDecimal purchasePrice, String shortDescription, String description) {
        Long companyId = i.getCompanyId();
        if (items.existsByCompanyIdAndSkuIgnoreCaseAndDeletedFalseAndIdNot(companyId, sku,
                i.getId() == null ? -1L : i.getId())) {
            throw ApiException.badRequest(EInventoryError.INV_010);
        }
        Category category = catalog.categoryOf(companyId, categoryId);
        Unit unit = catalog.unitOf(companyId, unitId);
        Tax tax = catalog.taxOf(companyId, taxId);
        i.setType(type);
        i.setName(name.trim());
        i.setSku(sku);
        i.setCategoryId(category.getId());
        i.setUnitId(unit.getId());
        i.setTaxId(tax == null ? null : tax.getId());
        i.setSalePrice(salePrice);
        i.setPurchasePrice(purchasePrice);
        i.setShortDescription(Texts.blankToNull(shortDescription));
        i.setDescription(Texts.blankToNull(description));
        return new Refs(category, unit, tax);
    }

    /** 8 random uppercase letters/digits, retried while the company already uses it. */
    String generateSku(Long companyId) {
        for (int attempt = 0; attempt < SKU_ATTEMPTS; attempt++) {
            StringBuilder b = new StringBuilder(SKU_LENGTH);
            for (int k = 0; k < SKU_LENGTH; k++) {
                b.append(SKU_ALPHABET.charAt(RANDOM.nextInt(SKU_ALPHABET.length())));
            }
            String sku = b.toString();
            if (!items.existsByCompanyIdAndSkuIgnoreCaseAndDeletedFalseAndIdNot(companyId, sku, -1L)) {
                return sku;
            }
        }
        throw ApiException.badRequest(EInventoryError.INV_021);
    }

    private BigDecimal quantity(Item i) {
        return i.getType().stocked() ? stocks.sumByItem(i.getId()) : BigDecimal.ZERO;
    }

    private static Set<Long> ids(List<Item> rows, Function<Item, Long> f) {
        Set<Long> s = new HashSet<>();
        rows.forEach(r -> {
            Long v = f.apply(r);
            if (v != null) {
                s.add(v);
            }
        });
        return s;
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static ItemDto dto(Item i, Category c, Unit u, Tax t, BigDecimal qty) {
        return new ItemDto(i.getId().toString(), i.getType(), i.getName(), i.getSku(), Texts.id(i.getTaxId()),
                t == null ? null : t.getName(), t == null ? null : t.getRate(), Texts.id(i.getCategoryId()),
                c == null ? null : c.getName(), Texts.id(i.getUnitId()), u == null ? null : u.getName(),
                i.getSalePrice(), i.getPurchasePrice(), i.getShortDescription(), i.getDescription(),
                i.getType().stocked() ? qty : BigDecimal.ZERO, i.getCreatedDate(), i.getUpdatedDate());
    }
}
