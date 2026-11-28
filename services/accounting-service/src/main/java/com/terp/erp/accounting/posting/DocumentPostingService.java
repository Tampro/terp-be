package com.terp.erp.accounting.posting;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalEntryRepository;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.ledger.JournalService.JournalEntryDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Journal entries of commerce documents (sales / purchase invoices and returns). commerce-service sends amounts by
 * role; the accounts come from the company's system account mapping, so the chief accountant can change them.
 * Idempotent per (company, sourceType, sourceId) through {@link JournalService#post}.
 * TODO(assumption): every account below (VAS TT200) is the PO's choice, to be confirmed by the chief accountant:
 *  sales invoice   Dr 131 total / Cr 5111 goods, Cr 5113 services (5111 when unmapped), Cr 33311 VAT;
 *                  Dr 632 / Cr 156 cost
 *  sales return    Dr 5213 net, Dr 33311 VAT / Cr 131 total; Dr 156 / Cr 632 cost
 *  purchase invoice Dr 156 goods, Dr 6428 services / non-stocked, Dr 1331 VAT / Cr 331 total
 *  purchase return Dr 331 total / Cr 156 goods, Cr 6428 services, Cr 1331 VAT
 */
@Service
@RequiredArgsConstructor
public class DocumentPostingService {
    private final JournalService journal;
    private final JournalEntryRepository entries;
    private final AccountMapping mapping;

    public enum ESource {
        SALES_INVOICE("SALES_POST"),
        SALES_RETURN("SALES_POST"),
        PURCHASE_INVOICE("PURCHASE_POST"),
        PURCHASE_RETURN("PURCHASE_POST"),
        /**
         * A paid Express order, requested only through order-service's outbox event (the intake skips this check).
         * ORDER_POST is held by no role, so the REST endpoint refuses it to staff (COMMON_004).
         */
        SHOP_ORDER("ORDER_POST");

        /** The commerce permission that posts / completes this document; required here too. */
        private final String permission;

        ESource(String permission) {
            this.permission = permission;
        }

        public String permission() {
            return permission;
        }
    }

    public record PostingForm(
            @Schema(description = "Kind of commerce document", example = "SALES_INVOICE",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ESource sourceType,
            @Schema(description = "The document id in commerce-service; with sourceType the idempotency key",
                    example = "40", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long sourceId,
            @Schema(description = "Posting date (invoice / return date)", example = "2026-11-22",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull java.time.LocalDate date,
            @Schema(description = "Document number", example = "SI-2026-11-001") @Size(max = 100) String reference,
            @Schema(example = "Sales invoice SI-2026-11-001 - Acme") @Size(max = 500) String description,
            @Schema(description = "Net of tax, after discount, of the stocked (goods) lines", example = "1000000")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal goodsAmount,
            @Schema(description = "Net of tax, after discount, of the service / non-stocked lines", example = "0")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal serviceAmount,
            @Schema(description = "VAT of the document", example = "100000")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal taxAmount,
            @Schema(description = "Sales invoice / return only: cost of the stocked lines", example = "700000")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal costAmount,
            @Schema(description = "true = check accounts, closed year and balance; write nothing", example = "false")
            boolean validateOnly) {
    }

    public record PostingDto(ESource sourceType, String sourceId, boolean validateOnly, String journalEntryId,
                             String journalNumber, JournalEntryDto entry) {
    }

    @Transactional
    public PostingDto post(Long requestedCompanyId, PostingForm f) {
        if (!CurrentUser.find().map(p -> p.has(f.sourceType().permission())).orElse(false)) {
            throw ApiException.forbidden(ECommonError.COMMON_004);
        }
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return postFor(companyId, f);
    }

    /**
     * The journal of a commerce document for a company, without a caller: used by the REST endpoint above after its
     * permission and tenant checks, and by {@link PostingEventIntake} for commerce.journal.requested (commerce checked
     * SALES_POST / PURCHASE_POST when it published the event; the company comes from the event).
     */
    @Transactional
    public PostingDto postFor(Long companyId, PostingForm f) {
        Optional<com.terp.erp.accounting.ledger.JournalEntry> existing = entries
                .findByCompanyIdAndSourceTypeAndSourceId(companyId, f.sourceType().name(), f.sourceId());
        if (existing.isPresent()) {
            // A retry (lost answer, or the commerce transaction failed after this one committed): nothing twice.
            JournalEntryDto e = journal.get(existing.get().getId());
            return new PostingDto(f.sourceType(), f.sourceId().toString(), f.validateOnly(), e.id(), e.number(), e);
        }
        List<JournalService.Line> lines = lines(companyId, f);
        journal.assertOpen(companyId, f.date());
        if (f.validateOnly()) {
            return new PostingDto(f.sourceType(), f.sourceId().toString(), true, null, null, null);
        }
        JournalEntryDto e = journal.post(companyId, f.date(), f.sourceType().name(), f.sourceId(), f.reference(),
                f.description(), lines);
        return new PostingDto(f.sourceType(), f.sourceId().toString(), false, e.id(), e.number(), e);
    }

    /** The balanced lines of the document; 400 ACC_017 for an unmapped key, ACC_019 when every amount is zero. */
    List<JournalService.Line> lines(Long c, PostingForm f) {
        BigDecimal goods = nz(f.goodsAmount());
        BigDecimal service = nz(f.serviceAmount());
        BigDecimal tax = nz(f.taxAmount());
        BigDecimal cost = nz(f.costAmount());
        BigDecimal total = goods.add(service).add(tax);
        String text = f.description();
        List<JournalService.Line> l = new ArrayList<>();
        switch (f.sourceType()) {
            case SALES_INVOICE -> {
                dr(l, () -> key(c, ESystemAccount.RECEIVABLE), total, text);
                cr(l, () -> key(c, ESystemAccount.SALES_REVENUE), goods, text);
                cr(l, () -> serviceRevenue(c), service, text);
                cr(l, () -> key(c, ESystemAccount.OUTPUT_VAT), tax, text);
                if (cost.signum() > 0) {
                    dr(l, () -> key(c, ESystemAccount.COGS), cost, text);
                    cr(l, () -> key(c, ESystemAccount.INVENTORY), cost, text);
                }
            }
            case SALES_RETURN -> {
                dr(l, () -> key(c, ESystemAccount.SALES_RETURNS), goods.add(service), text);
                dr(l, () -> key(c, ESystemAccount.OUTPUT_VAT), tax, text);
                cr(l, () -> key(c, ESystemAccount.RECEIVABLE), total, text);
                if (cost.signum() > 0) {
                    dr(l, () -> key(c, ESystemAccount.INVENTORY), cost, text);
                    cr(l, () -> key(c, ESystemAccount.COGS), cost, text);
                }
            }
            case PURCHASE_INVOICE -> {
                dr(l, () -> key(c, ESystemAccount.INVENTORY), goods, text);
                // TODO(assumption): purchased services / non-stocked items are expensed to 6428 (GENERAL_EXPENSE,
                //  the closest existing key); the chief accountant may want 6427 or 641x per item category.
                dr(l, () -> key(c, ESystemAccount.GENERAL_EXPENSE), service, text);
                dr(l, () -> key(c, ESystemAccount.INPUT_VAT), tax, text);
                cr(l, () -> key(c, ESystemAccount.PAYABLE), total, text);
            }
            case SHOP_ORDER -> {
                // TODO(assumption): money paid through the PG waits on PG_CLEARING (138) until the PG settles; fees
                //  (delivery, bag) are service revenue; prices include 10% VAT — chief accountant to confirm.
                dr(l, () -> key(c, ESystemAccount.PG_CLEARING), total, text);
                cr(l, () -> key(c, ESystemAccount.SALES_REVENUE), goods, text);
                cr(l, () -> serviceRevenue(c), service, text);
                cr(l, () -> key(c, ESystemAccount.OUTPUT_VAT), tax, text);
                if (cost.signum() > 0) {
                    dr(l, () -> key(c, ESystemAccount.COGS), cost, text);
                    cr(l, () -> key(c, ESystemAccount.INVENTORY), cost, text);
                }
            }
            case PURCHASE_RETURN -> {
                dr(l, () -> key(c, ESystemAccount.PAYABLE), total, text);
                cr(l, () -> key(c, ESystemAccount.INVENTORY), goods, text);
                cr(l, () -> key(c, ESystemAccount.GENERAL_EXPENSE), service, text);
                cr(l, () -> key(c, ESystemAccount.INPUT_VAT), tax, text);
            }
        }
        if (l.isEmpty()) {
            throw ApiException.badRequest(EAccountingError.ACC_019);
        }
        return l;
    }

    private Long serviceRevenue(Long c) {
        return mapping.find(c, ESystemAccount.SERVICE_REVENUE)
                .orElseGet(() -> mapping.require(c, ESystemAccount.SALES_REVENUE));
    }

    private Long key(Long c, ESystemAccount k) {
        return mapping.require(c, k);
    }

    /**
     * Zero amounts are left out (a line needs exactly one positive side), and their account is not even resolved: an
     * unmapped key only fails a document that needs it.
     */
    private static void dr(List<JournalService.Line> l, Supplier<Long> account, BigDecimal amount, String text) {
        if (amount.signum() > 0) {
            l.add(new JournalService.Line(account.get(), amount, null, text));
        }
    }

    private static void cr(List<JournalService.Line> l, Supplier<Long> account, BigDecimal amount, String text) {
        if (amount.signum() > 0) {
            l.add(new JournalService.Line(account.get(), null, amount, text));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
