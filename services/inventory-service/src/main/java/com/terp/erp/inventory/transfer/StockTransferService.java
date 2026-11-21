package com.terp.erp.inventory.transfer;

import com.terp.erp.exception.ApiException;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.common.Texts;
import com.terp.erp.inventory.item.Item;
import com.terp.erp.inventory.item.ItemRepository;
import com.terp.erp.inventory.stock.StockMovement;
import com.terp.erp.inventory.stock.StockService;
import com.terp.erp.inventory.stock.StockService.Change;
import com.terp.erp.inventory.warehouse.Warehouse;
import com.terp.erp.inventory.warehouse.WarehouseService;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Stock transfers between two warehouses of a company (ERP manual 8.4). No status and no edit: creating one moves
 * the stock at once, deleting one moves it back. Both go through StockService.apply, so they are all-or-nothing and
 * refused with INV_017 when a warehouse would go negative.
 */
@Service
@RequiredArgsConstructor
public class StockTransferService {
    private final StockTransferRepository transfers;
    private final WarehouseService warehouses;
    private final ItemRepository items;
    private final StockService stock;

    public record TransferForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long fromWarehouseId,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long toWarehouseId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long itemId,
            @Schema(example = "5", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal quantity,
            @Schema(example = "2026-11-21", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate transferDate) {
    }

    public record TransferDto(String id, String fromWarehouseId, String toWarehouseId, String itemId,
                              BigDecimal quantity, LocalDate transferDate, Instant createdDate) {
    }

    @Transactional(readOnly = true)
    public Page<TransferDto> search(Long requestedCompanyId, Long fromWarehouseId, Long toWarehouseId, Long itemId,
                                    LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        return transfers.search(Texts.company(requestedCompanyId), fromWarehouseId, toWarehouseId, itemId, dateFrom,
                dateTo, pageable).map(StockTransferService::dto);
    }

    @Transactional(readOnly = true)
    public TransferDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public TransferDto create(Long requestedCompanyId, TransferForm form) {
        Long companyId = Texts.company(requestedCompanyId);
        if (Objects.equals(form.fromWarehouseId(), form.toWarehouseId())) {
            throw ApiException.badRequest(EInventoryError.INV_022);
        }
        Warehouse from = activeWarehouse(companyId, form.fromWarehouseId());
        Warehouse to = activeWarehouse(companyId, form.toWarehouseId());
        Item item = item(companyId, form.itemId());
        if (!item.getType().stocked()) {
            throw ApiException.badRequest(EInventoryError.INV_016);
        }
        StockTransfer t = new StockTransfer();
        t.setCompanyId(companyId);
        t.setFromWarehouseId(from.getId());
        t.setToWarehouseId(to.getId());
        t.setItemId(item.getId());
        t.setQuantity(form.quantity());
        t.setTransferDate(form.transferDate());
        t = transfers.save(t);
        stock.apply(companyId, List.of(new Change(item, from, form.quantity().negate()),
                new Change(item, to, form.quantity())), StockMovement.TRANSFER, t.getId());
        return dto(t);
    }

    // TODO(assumption): the manual only says "delete only"; deleting is taken to undo the transfer (stock back from
    //  the destination into the source, refused with INV_017 when the destination no longer holds it). Confirm with BA.
    @Transactional
    public void delete(Long id) {
        StockTransfer t = require(id);
        // Warehouse status is not checked: undoing must not depend on whether a warehouse was deactivated since.
        Warehouse from = warehouses.require(t.getFromWarehouseId());
        Warehouse to = warehouses.require(t.getToWarehouseId());
        Item item = items.findById(t.getItemId()).orElseThrow(() -> ApiException.notFound(EInventoryError.INV_009));
        stock.apply(t.getCompanyId(), List.of(new Change(item, to, t.getQuantity().negate()),
                new Change(item, from, t.getQuantity())), StockMovement.TRANSFER_DELETE, t.getId());
        t.setDeleted(true);
    }

    private StockTransfer require(Long id) {
        StockTransfer t = transfers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EInventoryError.INV_023));
        TenantGuard.assertSameCompany(t.getCompanyId(), EInventoryError.INV_023);
        return t;
    }

    /** Another company's warehouse answers 404 INV_007, also for a super admin acting on a named company. */
    private Warehouse activeWarehouse(Long companyId, Long id) {
        Warehouse w = warehouses.require(id);
        if (!Objects.equals(w.getCompanyId(), companyId)) {
            throw ApiException.notFound(EInventoryError.INV_007);
        }
        if (!w.isActive()) {
            throw ApiException.badRequest(EInventoryError.INV_015);
        }
        return w;
    }

    private Item item(Long companyId, Long id) {
        Item i = items.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EInventoryError.INV_009));
        TenantGuard.assertSameCompany(i.getCompanyId(), EInventoryError.INV_009);
        if (!Objects.equals(i.getCompanyId(), companyId)) {
            throw ApiException.notFound(EInventoryError.INV_009);
        }
        return i;
    }

    private static TransferDto dto(StockTransfer t) {
        return new TransferDto(Texts.id(t.getId()), Texts.id(t.getFromWarehouseId()), Texts.id(t.getToWarehouseId()),
                Texts.id(t.getItemId()), t.getQuantity(), t.getTransferDate(), t.getCreatedDate());
    }
}
