package com.terp.erp.inventory.warehouse;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.stock.WarehouseStockRepository;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Warehouses (TERP Core warehouses). A warehouse still holding stock cannot be deleted (INV_008). */
@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseRepository warehouses;
    private final WarehouseStockRepository stocks;

    public record WarehouseForm(
            @Schema(example = "Main warehouse", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(example = "1 Example Street", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 500) String address,
            @Schema(example = "Ho Chi Minh City", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 100) String city,
            @Schema(example = "700000", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 20) String zipCode,
            @Schema(example = "0900000000") @Size(max = 30) String phone,
            @Schema(example = "warehouse@example.com") @Email @Size(max = 255) String email,
            @Schema(description = "Default true") Boolean active) {
    }

    public record WarehouseDto(String id, String name, String address, String city, String zipCode, String phone,
                               String email, boolean active, Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<WarehouseDto> search(Long requestedCompanyId, String keyword, String city, Boolean active,
                                     Pageable pageable) {
        String c = city == null ? "" : city.trim().toLowerCase(java.util.Locale.ROOT);
        return warehouses.search(Texts.company(requestedCompanyId), Texts.kw(keyword), c, active, pageable)
                .map(WarehouseService::dto);
    }

    @Transactional(readOnly = true)
    public WarehouseDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public WarehouseDto create(Long requestedCompanyId, WarehouseForm form) {
        Warehouse w = new Warehouse();
        w.setCompanyId(Texts.company(requestedCompanyId));
        apply(w, form);
        return dto(warehouses.save(w));
    }

    /** Full replacement: a missing active becomes true. */
    @Transactional
    public WarehouseDto update(Long id, WarehouseForm form) {
        Warehouse w = require(id);
        apply(w, form);
        return dto(w);
    }

    @Transactional
    public void delete(Long id) {
        Warehouse w = require(id);
        if (stocks.existsByWarehouseIdAndQuantityGreaterThan(w.getId(), BigDecimal.ZERO)) {
            throw ApiException.badRequest(EInventoryError.INV_008);
        }
        w.setDeleted(true);
    }

    /** A warehouse named in a stock change: active and of the company, else 400 INV_015. */
    public Warehouse activeOf(Long companyId, Long id) {
        return warehouses.findByIdAndDeletedFalse(id)
                .filter(w -> w.isActive() && Objects.equals(w.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EInventoryError.INV_015));
    }

    /** By-id access: another company's warehouse answers 404. */
    public Warehouse require(Long id) {
        Warehouse w = warehouses.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EInventoryError.INV_007));
        TenantGuard.assertSameCompany(w.getCompanyId(), EInventoryError.INV_007);
        return w;
    }

    private static void apply(Warehouse w, WarehouseForm f) {
        w.setName(f.name().trim());
        w.setAddress(f.address().trim());
        w.setCity(f.city().trim());
        w.setZipCode(f.zipCode().trim());
        w.setPhone(Texts.blankToNull(f.phone()));
        w.setEmail(Texts.blankToNull(f.email()));
        w.setActive(f.active() == null || f.active());
    }

    private static WarehouseDto dto(Warehouse w) {
        return new WarehouseDto(w.getId().toString(), w.getName(), w.getAddress(), w.getCity(), w.getZipCode(),
                w.getPhone(), w.getEmail(), w.isActive(), w.getCreatedDate(), w.getUpdatedDate());
    }
}
