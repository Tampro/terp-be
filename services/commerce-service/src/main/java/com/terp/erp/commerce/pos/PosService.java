package com.terp.erp.commerce.pos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.InventoryClient;
import com.terp.erp.commerce.common.EPosError;
import com.terp.erp.commerce.common.Money;
import com.terp.erp.commerce.common.Numbering;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POS checkout (ERP manual 10): prices and taxes come from the items, stock leaves the chosen warehouse and the total
 * is recorded as income in the chosen bank account. The order is written only when both succeeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosService {
    static final String STOCK_SOURCE = "POS_ORDER";
    /** Puts the stock back when the income could not be recorded after the stock had left. */
    static final String STOCK_UNDO_SOURCE = "POS_ORDER_VOID";
    static final String INCOME_SOURCE = "POS_ORDER";
    static final String WALK_IN = "Walk-in Customer";
    private static final String PREFIX = "POS";
    private static final String RECEIPT_PREFIX = "RCPT";
    // TODO(assumption): no limit in the manual; 300 labels (about 30 A4 pages) keeps one request bounded.
    static final int MAX_LABELS = 300;

    private final PosOrderRepository orders;
    private final Numbering numbering;
    private final InventoryClient inventory;
    private final AccountingClient accounting;
    private final Clock clock;

    public record CartLine(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long itemId,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal quantity) {
    }

    public record CartForm(
            @Schema(description = "Super admin only: the company to act on") Long companyId,
            @Schema(description = "accounting customer; null = Walk-in Customer", example = "3") @Positive
            Long customerId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long warehouseId,
            @Schema(description = "Required to pay (POS_003); optional for a preview", example = "1") @Positive
            Long bankAccountId,
            @Schema(description = "Amount off the whole order (not a percentage)", example = "0") @DecimalMin("0")
            @Digits(integer = 15, fraction = 2) BigDecimal discount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 200)
            List<@Valid @NotNull CartLine> lines) {
    }

    public record LineDto(String itemId, String itemType, String itemName, String sku, BigDecimal quantity,
                          BigDecimal unitPrice, String taxName, BigDecimal taxRate, BigDecimal amount,
                          BigDecimal taxAmount) {
        static LineDto of(PosOrder.Line l) {
            return new LineDto(l.getItemId().toString(), l.getItemType(), l.getItemName(), l.getSku(),
                    l.getQuantity(), l.getUnitPrice(), l.getTaxName(), l.getTaxRate(), l.getAmount(),
                    l.getTaxAmount());
        }
    }

    /** Tax total per tax name, for the "taxes" rows of the cart and the receipt. */
    public record TaxDto(String name, BigDecimal rate, BigDecimal amount) {
    }

    public record PreviewDto(String customerName, String warehouseName, BigDecimal subtotal, List<TaxDto> taxes,
                             BigDecimal taxTotal, BigDecimal discount, BigDecimal total, List<LineDto> lines) {
    }

    public record OrderRow(String id, String number, String receiptNumber, LocalDate orderDate, String customerId,
                           String customerName, String warehouseId, String warehouseName, int items,
                           BigDecimal total, Instant createdDate) {
        static OrderRow of(PosOrder o) {
            return new OrderRow(o.getId().toString(), o.getNumber(), o.getReceiptNumber(), o.getOrderDate(),
                    PosService.id(o.getCustomerId()), o.getCustomerName(), o.getWarehouseId().toString(), o.getWarehouseName(),
                    o.getLines().size(), o.getTotal(), o.getCreatedDate());
        }
    }

    public record OrderDetail(OrderRow order, String bankAccountId, String bankAccountName, BigDecimal subtotal,
                              List<TaxDto> taxes, BigDecimal taxTotal, BigDecimal discount, List<LineDto> lines) {
        static OrderDetail of(PosOrder o) {
            return new OrderDetail(OrderRow.of(o), o.getBankAccountId().toString(), o.getBankAccountName(),
                    o.getSubtotal(), PosService.taxes(o.getLines()), o.getTaxTotal(), o.getDiscount(),
                    o.getLines().stream().map(LineDto::of).toList());
        }
    }

    public record LabelLine(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long itemId,
            @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive
            @jakarta.validation.constraints.Max(MAX_LABELS) Integer copies) {
    }

    public record BarcodeForm(
            @Schema(description = "The warehouse the products were picked from (checked, not printed)", example = "1")
            @Positive Long warehouseId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 200)
            List<@Valid @NotNull LabelLine> items) {
    }

    /** The receipt PDF of an order. */
    @Transactional(readOnly = true)
    public byte[] receiptPdf(Long id) {
        return PosPdf.receipt(OrderDetail.of(require(id)));
    }

    /** Label sheet: each item's SKU as Code 128, repeated copies times (at most MAX_LABELS labels in all). */
    public byte[] barcodesPdf(BarcodeForm f) {
        int total = f.items().stream().mapToInt(LabelLine::copies).sum();
        if (total > MAX_LABELS) {
            throw ApiException.badRequest(EPosError.POS_008, MAX_LABELS);
        }
        if (f.warehouseId() != null) {
            inventory.warehouse(f.warehouseId());
        }
        List<PosPdf.Label> labels = new ArrayList<>();
        for (LabelLine l : f.items()) {
            InventoryClient.Item item = inventory.item(l.itemId());
            for (int k = 0; k < l.copies(); k++) {
                labels.add(new PosPdf.Label(item.name(), item.sku(), Money.nz(item.salePrice())));
            }
        }
        return PosPdf.barcodes(labels);
    }

    /** The cart's totals as the checkout screen shows them; nothing is written, no stock reserved. */
    @Transactional(readOnly = true)
    public PreviewDto preview(CartForm f) {
        TenantGuard.resolveCompany(f.companyId(), EPosError.POS_002);
        PosOrder o = build(f);
        return new PreviewDto(o.getCustomerName(), o.getWarehouseName(), o.getSubtotal(), taxes(o.getLines()),
                o.getTaxTotal(), o.getDiscount(), o.getTotal(), o.getLines().stream().map(LineDto::of).toList());
    }

    /**
     * Pay: stock leaves the warehouse (all-or-nothing, never below zero), then the total is recorded as income in
     * the bank account with one journal entry (Dr bank total / Cr 5111 total - VAT / Cr 33311 VAT; Dr 632 / Cr 156 at
     * cost). If the income fails after the stock left (closed year ACC_070 included), the stock is put back and the
     * sale is refused.
     * TODO(assumption): cost = item purchase price x quantity (costing method is an open ARQ question).
     */
    @Transactional
    public OrderDetail checkout(CartForm f) {
        Long companyId = TenantGuard.resolveCompany(f.companyId(), EPosError.POS_002);
        if (f.bankAccountId() == null) {
            throw ApiException.badRequest(EPosError.POS_003);
        }
        AccountingClient.BankAccount bank = accounting.bankAccount(f.bankAccountId());
        if (!bank.active()) {
            throw ApiException.badRequest(EPosError.POS_004);
        }
        PosOrder o = build(f);
        o.setCompanyId(companyId);
        o.setBankAccountId(f.bankAccountId());
        o.setBankAccountName(bank.label());
        o.setOrderDate(today());
        o.setNumber(numbering.next(companyId, PREFIX, o.getOrderDate()));
        o.setReceiptNumber(numbering.next(companyId, RECEIPT_PREFIX, o.getOrderDate()));
        PosOrder saved = orders.saveAndFlush(o);

        List<InventoryClient.MoveLine> out = moves(saved, BigDecimal.ONE.negate());
        inventory.move(STOCK_SOURCE, saved.getId(), out);
        if (saved.getTotal().signum() > 0) {
            try {
                accounting.income(saved.getBankAccountId(), INCOME_SOURCE, saved.getId(), saved.getTotal(),
                        saved.getTaxTotal(), o.getCostTotal(), saved.getOrderDate(), "POS sale " + saved.getNumber());
            } catch (RuntimeException e) {
                undoStock(saved);
                throw e;
            }
        }
        return OrderDetail.of(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderRow> search(Long companyId, Long customerId, Long warehouseId, LocalDate from, LocalDate to,
                                 String keyword, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EPosError.POS_002);
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return orders.search(company, customerId, warehouseId, from, to, kw, pageable).map(OrderRow::of);
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long id) {
        return OrderDetail.of(require(id));
    }

    /** By-id access: another company's order answers 404 (POS_001). */
    PosOrder require(Long id) {
        PosOrder o = orders.findById(id).orElseThrow(() -> ApiException.notFound(EPosError.POS_001));
        TenantGuard.assertSameCompany(o.getCompanyId(), EPosError.POS_001);
        return o;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Lines priced from the items (same item twice = one line), totals, customer and warehouse names. */
    private PosOrder build(CartForm f) {
        InventoryClient.Warehouse wh = inventory.warehouse(f.warehouseId());
        if (!wh.active()) {
            throw ApiException.badRequest(EPosError.POS_006);
        }
        PosOrder o = new PosOrder();
        o.setWarehouseId(f.warehouseId());
        o.setWarehouseName(wh.name());
        o.setCustomerId(f.customerId());
        o.setCustomerName(f.customerId() == null ? WALK_IN : accounting.customer(f.customerId()).companyName());

        Map<Long, BigDecimal> qty = new LinkedHashMap<>();
        for (CartLine l : f.lines()) {
            qty.merge(l.itemId(), l.quantity(), BigDecimal::add);
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        int position = 0;
        for (Map.Entry<Long, BigDecimal> e : qty.entrySet()) {
            InventoryClient.Item item = inventory.item(e.getKey());
            Money.Line m = Money.line(e.getValue(), Money.nz(item.salePrice()), null, item.taxRate());
            PosOrder.Line l = new PosOrder.Line();
            l.setPosition(position++);
            l.setItemId(e.getKey());
            l.setItemType(item.type());
            l.setItemName(item.name());
            l.setSku(item.sku());
            l.setQuantity(e.getValue());
            l.setUnitPrice(Money.nz(item.salePrice()));
            l.setTaxName(item.taxName());
            l.setTaxRate(Money.nz(item.taxRate()));
            l.setAmount(m.base());
            l.setTaxAmount(m.tax());
            o.getLines().add(l);
            subtotal = subtotal.add(m.base());
            tax = tax.add(m.tax());
            if (l.stocked()) {
                cost = cost.add(Money.round(Money.nz(item.purchasePrice()).multiply(e.getValue())));
            }
        }
        BigDecimal discount = Money.round(Money.nz(f.discount()));
        // TODO(assumption): the order discount is an amount taken off after taxes (taxes stay on the full price).
        if (discount.compareTo(subtotal.add(tax)) > 0) {
            throw ApiException.badRequest(EPosError.POS_005);
        }
        o.setSubtotal(subtotal);
        o.setTaxTotal(tax);
        o.setCostTotal(cost);
        o.setDiscount(discount);
        o.setTotal(subtotal.add(tax).subtract(discount));
        return o;
    }

    private static List<InventoryClient.MoveLine> moves(PosOrder o, BigDecimal sign) {
        List<InventoryClient.MoveLine> lines = new ArrayList<>();
        for (PosOrder.Line l : o.getLines()) {
            if (l.stocked()) {
                lines.add(new InventoryClient.MoveLine(l.getItemId(), o.getWarehouseId(), l.getQuantity().multiply(sign)));
            }
        }
        return lines;
    }

    /** Best effort: a failure here is logged for manual correction, the caller still gets the income error. */
    private void undoStock(PosOrder o) {
        try {
            inventory.move(STOCK_UNDO_SOURCE, o.getId(), moves(o, BigDecimal.ONE));
        } catch (RuntimeException undo) {
            log.error("POS order {} (company {}): stock left warehouse {} but the income failed and the stock could "
                    + "not be put back: {}", o.getId(), o.getCompanyId(), o.getWarehouseId(),
                    undo.getClass().getSimpleName());
        }
    }

    static List<TaxDto> taxes(List<PosOrder.Line> lines) {
        Map<String, TaxDto> m = new LinkedHashMap<>();
        for (PosOrder.Line l : lines) {
            if (l.getTaxAmount().signum() == 0) {
                continue;
            }
            String name = l.getTaxName() == null ? "Tax" : l.getTaxName();
            String key = name + "|" + l.getTaxRate().stripTrailingZeros().toPlainString();
            m.merge(key, new TaxDto(name, l.getTaxRate(), l.getTaxAmount()),
                    (a, b) -> new TaxDto(a.name(), a.rate(), a.amount().add(b.amount())));
        }
        return List.copyOf(m.values());
    }

    private static String id(Long v) {
        return v == null ? null : v.toString();
    }
}
