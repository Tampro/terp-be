package com.terp.erp.commerce.salesreturn;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.Money;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.sales.EInvoiceType;
import com.terp.erp.commerce.sales.SalesDocument;
import com.terp.erp.commerce.sales.SalesDocumentLine;
import com.terp.erp.commerce.sales.SalesDocumentService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sales returns (ERP manual 6.3). A line returns part of one invoice line, never more than is still returnable
 * (invoiced minus every other non-deleted return). Completing puts the stocked lines back (inventory-service,
 * idempotent on the return id).
 */
@Service
@RequiredArgsConstructor
public class SalesReturnService {
    static final String STOCK_SOURCE = "SALES_RETURN";

    private final SalesReturnRepository returns;
    private final SalesDocumentService documents;
    private final Numbering numbering;
    private final InventoryClient inventory;
    private final Clock clock;

    public record LineForm(
            @Schema(description = "Id of the invoice line returned", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long invoiceLineId,
            @Schema(description = "Quantity returned", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Schema(description = "Reason for this line", example = "Damaged on arrival") @Size(max = 255) String reason) {
    }

    /** Create and full-replacement edit of a draft return. */
    public record ReturnForm(
            @Schema(description = "Posted invoice the goods come from", example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long invoiceId,
            @Schema(description = "Receiving warehouse; omit = the invoice's warehouse. None for a service-wise invoice.",
                    example = "2") Long warehouseId,
            @Schema(description = "Return date", example = "2026-11-25", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate returnDate,
            @Schema(description = "Return reason", example = "Excess quantity") @Size(max = 255) String reason,
            @Schema(description = "Notes", example = "Picked up by courier") @Size(max = 5000) String notes,
            @Schema(description = "Returned lines, 1 to 200", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty @Size(max = 200) List<@Valid @NotNull LineForm> lines) {
    }

    public record LineDto(String id, String invoiceLineId, String itemId, String itemType, String itemName, String sku,
                          BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal taxRate,
                          BigDecimal discount, BigDecimal taxAmount, BigDecimal lineTotal, String reason) {
        static LineDto of(SalesReturn.Line l) {
            return new LineDto(String.valueOf(l.getId()), l.getInvoiceLineId().toString(), l.getItemId().toString(),
                    l.getItemType(), l.getItemName(), l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(),
                    l.getTaxRate(), l.getDiscount(), l.getTaxAmount(), l.getLineTotal(), l.getReason());
        }
    }

    public record ReturnRow(String id, String number, String invoiceId, String customerId, String customerName,
                            String warehouseId, LocalDate returnDate, BigDecimal total, int items,
                            SalesReturn.EStatus status, Instant createdDate) {
        static ReturnRow of(SalesReturn r) {
            return new ReturnRow(r.getId().toString(), r.getNumber(), r.getInvoiceId().toString(),
                    r.getCustomerId().toString(), r.getCustomerName(),
                    r.getWarehouseId() == null ? null : r.getWarehouseId().toString(), r.getReturnDate(), r.getTotal(),
                    r.getLines().size(), r.getStatus(), r.getCreatedDate());
        }
    }

    public record ReturnDetail(ReturnRow ret, String reason, String notes, BigDecimal subtotal, BigDecimal discount,
                               BigDecimal tax, List<LineDto> lines) {
        static ReturnDetail of(SalesReturn r) {
            return new ReturnDetail(ReturnRow.of(r), r.getReason(), r.getNotes(), r.getSubtotal(),
                    r.getDiscountTotal(), r.getTaxTotal(), r.getLines().stream().map(LineDto::of).toList());
        }
    }

    /** One invoice line with what can still be returned, for the create form. */
    public record ReturnableLine(String invoiceLineId, String itemId, String itemName, String sku, BigDecimal invoiced,
                                 BigDecimal returnable, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal taxRate) {
    }

    @Transactional(readOnly = true)
    public Page<ReturnRow> search(Long companyId, Long invoiceId, Long customerId, Long warehouseId,
                                  SalesReturn.EStatus status, LocalDate from, LocalDate to, String keyword,
                                  Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, ECommerceError.SAL_006);
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return returns.search(company, invoiceId, customerId, warehouseId, status, from, to, kw, pageable)
                .map(ReturnRow::of);
    }

    @Transactional(readOnly = true)
    public ReturnDetail get(Long id) {
        return ReturnDetail.of(require(id));
    }

    @Transactional(readOnly = true)
    public List<ReturnableLine> returnable(Long invoiceId) {
        SalesDocument inv = documents.requirePostedInvoice(invoiceId);
        Map<Long, BigDecimal> done = returns.returned(ids(inv), null);
        return inv.getLines().stream().map(l -> new ReturnableLine(l.getId().toString(), l.getItemId().toString(),
                l.getItemName(), l.getSku(), l.getQuantity(),
                l.getQuantity().subtract(done.getOrDefault(l.getId(), BigDecimal.ZERO)), l.getUnitPrice(),
                l.getDiscountPct(), l.getTaxRate())).toList();
    }

    @Transactional
    public ReturnDetail create(ReturnForm f) {
        SalesDocument inv = documents.requirePostedInvoice(f.invoiceId());
        SalesReturn r = new SalesReturn();
        r.setCompanyId(inv.getCompanyId());
        apply(r, inv, f, null);
        r.setNumber(numbering.next(inv.getCompanyId(), "SR", f.returnDate()));
        return ReturnDetail.of(returns.save(r));
    }

    /** Full replacement of a draft return; the invoice may change too. */
    @Transactional
    public ReturnDetail update(Long id, ReturnForm f) {
        SalesReturn r = requireDraft(id);
        SalesDocument inv = documents.requirePostedInvoice(f.invoiceId());
        apply(r, inv, f, r.getId());
        return ReturnDetail.of(r);
    }

    @Transactional
    public void delete(Long id) {
        requireDraft(id).setDeleted(true);
    }

    @Transactional
    public ReturnDetail approve(Long id) {
        SalesReturn r = requireDraft(id);
        r.setStatus(SalesReturn.EStatus.APPROVED);
        return ReturnDetail.of(r);
    }

    /** APPROVED → COMPLETED once the goods are received: stocked lines go back into the receiving warehouse. */
    @Transactional
    public ReturnDetail complete(Long id) {
        SalesReturn r = require(id);
        if (r.getStatus() != SalesReturn.EStatus.APPROVED) {
            throw ApiException.badRequest(ECommerceError.SAL_014);
        }
        if (r.getWarehouseId() != null) {
            inventory.move(STOCK_SOURCE, r.getId(), r.getLines().stream().filter(SalesReturn.Line::stocked)
                    .map(l -> new InventoryClient.MoveLine(l.getItemId(), r.getWarehouseId(), l.getQuantity()))
                    .toList());
        }
        r.setStatus(SalesReturn.EStatus.COMPLETED);
        r.setCompletedAt(Instant.now(clock));
        return ReturnDetail.of(r);
    }

    private void apply(SalesReturn r, SalesDocument inv, ReturnForm f, Long excludeId) {
        boolean service = inv.getInvoiceType() == EInvoiceType.SERVICE;
        Long warehouse = service ? null : (f.warehouseId() == null ? inv.getWarehouseId() : f.warehouseId());
        if (service && f.warehouseId() != null) {
            throw ApiException.badRequest(ECommerceError.SAL_019);
        }
        Map<Long, SalesDocumentLine> byId = inv.getLines().stream()
                .collect(Collectors.toMap(SalesDocumentLine::getId, Function.identity()));
        Map<Long, BigDecimal> done = returns.returned(ids(inv), excludeId);
        Map<Long, BigDecimal> asked = new java.util.HashMap<>();
        List<SalesReturn.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        int pos = 1;
        for (LineForm lf : f.lines()) {
            SalesDocumentLine src = byId.get(lf.invoiceLineId());
            if (src == null) {
                throw ApiException.badRequest(ECommerceError.SAL_018, lf.invoiceLineId());
            }
            BigDecimal left = src.getQuantity().subtract(done.getOrDefault(src.getId(), BigDecimal.ZERO))
                    .subtract(asked.getOrDefault(src.getId(), BigDecimal.ZERO));
            if (lf.quantity().compareTo(left) > 0) {
                throw ApiException.badRequest(ECommerceError.SAL_011, src.getSku() == null ? src.getItemName()
                        : src.getSku(), left.stripTrailingZeros().toPlainString());
            }
            asked.merge(src.getId(), lf.quantity(), BigDecimal::add);
            Money.Line m = Money.line(lf.quantity(), src.getUnitPrice(), src.getDiscountPct(), src.getTaxRate());
            SalesReturn.Line l = new SalesReturn.Line();
            l.setPosition(pos++);
            l.setInvoiceLineId(src.getId());
            l.setItemId(src.getItemId());
            l.setItemType(src.getItemType());
            l.setItemName(src.getItemName());
            l.setSku(src.getSku());
            l.setQuantity(lf.quantity());
            l.setUnitPrice(src.getUnitPrice());
            l.setDiscountPct(src.getDiscountPct());
            l.setTaxRate(src.getTaxRate());
            l.setDiscount(m.discount());
            l.setTaxAmount(m.tax());
            l.setLineTotal(m.total());
            l.setReason(blank(lf.reason()));
            lines.add(l);
            subtotal = subtotal.add(m.base());
            discount = discount.add(m.discount());
            tax = tax.add(m.tax());
        }
        r.setInvoiceId(inv.getId());
        r.setCustomerId(inv.getCustomerId());
        r.setCustomerName(inv.getCustomerName());
        r.setWarehouseId(warehouse);
        r.setReturnDate(f.returnDate());
        r.setReason(blank(f.reason()));
        r.setNotes(blank(f.notes()));
        r.getLines().clear();
        r.getLines().addAll(lines);
        r.setSubtotal(subtotal);
        r.setDiscountTotal(discount);
        r.setTaxTotal(tax);
        r.setTotal(subtotal.subtract(discount).add(tax));
    }

    private SalesReturn require(Long id) {
        SalesReturn r = returns.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_012));
        TenantGuard.assertSameCompany(r.getCompanyId(), ECommerceError.SAL_012);
        return r;
    }

    private SalesReturn requireDraft(Long id) {
        SalesReturn r = require(id);
        if (r.getStatus() != SalesReturn.EStatus.DRAFT) {
            throw ApiException.badRequest(ECommerceError.SAL_013);
        }
        return r;
    }

    private static List<Long> ids(SalesDocument inv) {
        return inv.getLines().stream().map(SalesDocumentLine::getId).toList();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
