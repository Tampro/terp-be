package com.terp.erp.commerce.purchase;

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
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.Money;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.commerce.common.Postings;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase returns (ERP manual 8.2): number PR-YYYY-MM-NNN. A line returns part of one invoice line, never more than
 * is still returnable (invoiced minus every other non-deleted return). Completing takes the stocked lines out of the
 * sending warehouse (inventory-service, idempotent on the return id; not enough stock → its 400, relayed).
 */
@Service
@RequiredArgsConstructor
public class PurchaseReturnService {
    static final String STOCK_SOURCE = "PURCHASE_RETURN";
    static final String PREFIX = "PR";

    private final PurchaseReturnRepository returns;
    private final PurchaseInvoiceService invoices;
    private final Numbering numbering;
    private final InventoryClient inventory;
    private final AccountingClient accounting;
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
            @Schema(description = "Posted purchase invoice the goods came on", example = "40",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long invoiceId,
            @Schema(description = "Sending warehouse; omit = the invoice's receiving warehouse", example = "2")
            Long warehouseId,
            @Schema(description = "Return date", example = "2026-11-25", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate returnDate,
            @Schema(description = "Return reason", example = "Wrong model delivered") @Size(max = 255) String reason,
            @Schema(description = "Notes", example = "Vendor collects on Friday") @Size(max = 5000) String notes,
            @Schema(description = "Returned lines, 1 to 200", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty @Size(max = 200) List<@Valid @NotNull LineForm> lines) {
    }

    public record LineDto(String id, String invoiceLineId, String itemId, String itemType, String itemName, String sku,
                          BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal taxRate,
                          BigDecimal discount, BigDecimal taxAmount, BigDecimal lineTotal, String reason) {
        static LineDto of(PurchaseReturn.Line l) {
            return new LineDto(String.valueOf(l.getId()), l.getInvoiceLineId().toString(), l.getItemId().toString(),
                    l.getItemType(), l.getItemName(), l.getSku(), l.getQuantity(), l.getUnitPrice(), l.getDiscountPct(),
                    l.getTaxRate(), l.getDiscount(), l.getTaxAmount(), l.getLineTotal(), l.getReason());
        }
    }

    public record ReturnRow(String id, String number, String invoiceId, String vendorId, String vendorName,
                            String warehouseId, LocalDate returnDate, BigDecimal total, int items,
                            PurchaseReturn.EStatus status, Instant createdDate) {
        static ReturnRow of(PurchaseReturn r) {
            return new ReturnRow(r.getId().toString(), r.getNumber(), r.getInvoiceId().toString(),
                    r.getVendorId().toString(), r.getVendorName(),
                    r.getWarehouseId() == null ? null : r.getWarehouseId().toString(), r.getReturnDate(), r.getTotal(),
                    r.getLines().size(), r.getStatus(), r.getCreatedDate());
        }
    }

    public record ReturnDetail(ReturnRow ret, String reason, String notes, BigDecimal subtotal, BigDecimal discount,
                               BigDecimal tax, List<LineDto> lines) {
        static ReturnDetail of(PurchaseReturn r) {
            return new ReturnDetail(ReturnRow.of(r), r.getReason(), r.getNotes(), r.getSubtotal(),
                    r.getDiscountTotal(), r.getTaxTotal(), r.getLines().stream().map(LineDto::of).toList());
        }
    }

    /** One invoice line with what can still be returned, for the create form. */
    public record ReturnableLine(String invoiceLineId, String itemId, String itemName, String sku, BigDecimal invoiced,
                                 BigDecimal returnable, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal taxRate) {
    }

    @Transactional(readOnly = true)
    public Page<ReturnRow> search(Long companyId, Long invoiceId, Long vendorId, Long warehouseId,
                                  PurchaseReturn.EStatus status, LocalDate from, LocalDate to, String keyword,
                                  Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EPurchaseError.PUR_003);
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return returns.search(company, invoiceId, vendorId, warehouseId, status, from, to, kw, pageable)
                .map(ReturnRow::of);
    }

    @Transactional(readOnly = true)
    public ReturnDetail get(Long id) {
        return ReturnDetail.of(require(id));
    }

    /** Lines of a posted invoice with their returnable quantity; PUR_012 when nothing is left to return. */
    @Transactional(readOnly = true)
    public List<ReturnableLine> returnable(Long invoiceId) {
        PurchaseInvoice inv = invoices.requirePosted(invoiceId);
        Map<Long, BigDecimal> done = returns.returned(ids(inv), null);
        List<ReturnableLine> out = inv.getLines().stream().map(l -> new ReturnableLine(l.getId().toString(),
                l.getItemId().toString(), l.getItemName(), l.getSku(), l.getQuantity(),
                l.getQuantity().subtract(done.getOrDefault(l.getId(), BigDecimal.ZERO)), l.getUnitPrice(),
                l.getDiscountPct(), l.getTaxRate())).toList();
        if (out.stream().allMatch(l -> l.returnable().signum() <= 0)) {
            throw ApiException.badRequest(EPurchaseError.PUR_012);
        }
        return out;
    }

    @Transactional
    public ReturnDetail create(ReturnForm f) {
        PurchaseInvoice inv = invoices.lockPosted(f.invoiceId());
        PurchaseReturn r = new PurchaseReturn();
        r.setCompanyId(inv.getCompanyId());
        apply(r, inv, f, null);
        r.setNumber(numbering.next(inv.getCompanyId(), PREFIX, f.returnDate()));
        return ReturnDetail.of(returns.save(r));
    }

    /** Full replacement of a draft return; the invoice may change too. */
    @Transactional
    public ReturnDetail update(Long id, ReturnForm f) {
        PurchaseReturn r = requireDraft(id);
        PurchaseInvoice inv = invoices.lockPosted(f.invoiceId());
        apply(r, inv, f, r.getId());
        return ReturnDetail.of(r);
    }

    @Transactional
    public void delete(Long id) {
        requireDraft(id).setDeleted(true);
    }

    @Transactional
    public ReturnDetail approve(Long id) {
        PurchaseReturn r = requireDraft(id);
        r.setStatus(PurchaseReturn.EStatus.APPROVED);
        return ReturnDetail.of(r);
    }

    /**
     * APPROVED → COMPLETED once the goods have left: stocked lines go out of the sending warehouse and the return is
     * journalled (Dr 331 total / Cr 156 goods, Cr 6428 services, Cr 1331 VAT), in the order of {@link Postings}; a
     * closed year (ACC_070) is relayed, the return stays APPROVED.
     * TODO(assumption): goods leave inventory at the purchase price of the invoice line (net of discount).
     */
    @Transactional
    public ReturnDetail complete(Long id) {
        PurchaseReturn r = require(id);
        if (r.getStatus() != PurchaseReturn.EStatus.APPROVED) {
            throw ApiException.badRequest(EPurchaseError.PUR_010);
        }
        List<InventoryClient.MoveLine> moves = r.getLines().stream().filter(PurchaseReturn.Line::stocked)
                .map(l -> new InventoryClient.MoveLine(l.getItemId(), r.getWarehouseId(), l.getQuantity().negate()))
                .toList();
        if (!moves.isEmpty() && r.getWarehouseId() == null) {
            throw ApiException.badRequest(EPurchaseError.PUR_005);
        }
        BigDecimal goods = BigDecimal.ZERO, service = BigDecimal.ZERO;
        for (PurchaseReturn.Line l : r.getLines()) {
            BigDecimal net = l.getLineTotal().subtract(l.getTaxAmount());
            if (l.stocked()) {
                goods = goods.add(net);
            } else {
                service = service.add(net);
            }
        }
        AccountingClient.Posting posting = new AccountingClient.Posting(STOCK_SOURCE, r.getId(), r.getReturnDate(),
                r.getNumber(), "Purchase return " + r.getNumber() + " - " + r.getVendorName(), goods, service,
                r.getTaxTotal(), null);
        Postings.run(accounting, posting, () -> inventory.move(STOCK_SOURCE, r.getId(), moves));
        r.setStatus(PurchaseReturn.EStatus.COMPLETED);
        r.setCompletedAt(Instant.now(clock));
        return ReturnDetail.of(r);
    }

    private void apply(PurchaseReturn r, PurchaseInvoice inv, ReturnForm f, Long excludeId) {
        Map<Long, PurchaseInvoice.Line> byId = inv.getLines().stream()
                .collect(Collectors.toMap(PurchaseInvoice.Line::getId, Function.identity()));
        Map<Long, BigDecimal> done = returns.returned(ids(inv), excludeId);
        Map<Long, BigDecimal> asked = new HashMap<>();
        List<PurchaseReturn.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        int pos = 1;
        for (LineForm lf : f.lines()) {
            PurchaseInvoice.Line src = byId.get(lf.invoiceLineId());
            if (src == null) {
                throw ApiException.badRequest(EPurchaseError.PUR_011, lf.invoiceLineId());
            }
            BigDecimal left = src.getQuantity().subtract(done.getOrDefault(src.getId(), BigDecimal.ZERO))
                    .subtract(asked.getOrDefault(src.getId(), BigDecimal.ZERO));
            if (lf.quantity().compareTo(left) > 0) {
                throw ApiException.badRequest(EPurchaseError.PUR_007, src.getSku() == null ? src.getItemName()
                        : src.getSku(), left.stripTrailingZeros().toPlainString());
            }
            asked.merge(src.getId(), lf.quantity(), BigDecimal::add);
            Money.Line m = Money.line(lf.quantity(), src.getUnitPrice(), src.getDiscountPct(), src.getTaxRate());
            PurchaseReturn.Line l = new PurchaseReturn.Line();
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
        boolean stocked = lines.stream().anyMatch(PurchaseReturn.Line::stocked);
        r.setInvoiceId(inv.getId());
        r.setVendorId(inv.getVendorId());
        r.setVendorName(inv.getVendorName());
        r.setWarehouseId(!stocked ? null : (f.warehouseId() == null ? inv.getWarehouseId() : f.warehouseId()));
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

    private PurchaseReturn require(Long id) {
        PurchaseReturn r = returns.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPurchaseError.PUR_008));
        TenantGuard.assertSameCompany(r.getCompanyId(), EPurchaseError.PUR_008);
        return r;
    }

    private PurchaseReturn requireDraft(Long id) {
        PurchaseReturn r = require(id);
        if (r.getStatus() != PurchaseReturn.EStatus.DRAFT) {
            throw ApiException.badRequest(EPurchaseError.PUR_009);
        }
        return r;
    }

    private static List<Long> ids(PurchaseInvoice inv) {
        return inv.getLines().stream().map(PurchaseInvoice.Line::getId).toList();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
