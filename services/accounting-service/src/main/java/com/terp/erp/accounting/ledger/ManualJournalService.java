package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.JournalService.JournalEntryDto;
import com.terp.erp.accounting.ledger.JournalService.Line;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Manual journal entries and reversals (ERP manual 13, FR-DE-002/003). A manual entry is a DRAFT (no balance moves;
 * edit, delete, post) or POSTED. Any POSTED entry, manual or automatic, can be reversed once: a new POSTED entry with
 * the sides swapped, linked both ways, and the original becomes REVERSED. Automatic entries are never edited or
 * deleted (FR-DE-001). Every date is checked against the closed-period lock.
 */
@Service
@RequiredArgsConstructor
public class ManualJournalService {
    static final String REVERSAL_SOURCE = "REVERSAL";

    private final JournalEntryRepository entries;
    private final ChartAccountRepository accounts;
    private final JournalService journal;
    private final Clock clock;

    public record LineForm(
            @Schema(description = "Ledger account id of the company", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long accountId,
            @Schema(description = "Line description", example = "Accrued rent November") @Size(max = 500)
            String description,
            @Schema(description = "Debit; exactly one of debit / credit is > 0", example = "1500000")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal debit,
            @Schema(description = "Credit; exactly one of debit / credit is > 0", example = "0")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal credit) {
    }

    public record ManualForm(
            @Schema(description = "Journal date; default today", example = "2026-11-22") LocalDate date,
            @Schema(description = "What the entry is for", example = "ADJUSTMENT",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EReferenceType referenceType,
            @Schema(description = "Free reference (document number ...)", example = "ADJ-11") @Size(max = 100)
            String reference,
            @Schema(description = "At least 5 characters", example = "Accrue November rent",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 5, max = 500)
            String description,
            @Schema(description = "At least two lines; total debit must equal total credit",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 200)
            List<@Valid @NotNull LineForm> lines,
            @Schema(description = "true = post now (balances move); false = save as DRAFT", example = "false")
            boolean post) {
    }

    public record ReverseForm(
            @Schema(description = "Reversal date; default today, not before the original's date",
                    example = "2026-11-30") LocalDate date,
            @Schema(description = "Default 'Reversal of JE-...'", example = "Wrong account") @Size(max = 500)
            String description) {
    }

    @Transactional
    public JournalEntryDto create(Long requestedCompanyId, ManualForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        LocalDate date = form.date() == null ? LocalDate.now(clock) : form.date();
        journal.assertOpen(companyId, date);
        JournalEntry e = new JournalEntry();
        e.setCompanyId(companyId);
        e.setNumber(journal.nextNumber(companyId, date));
        e.setType(EJournalType.MANUAL);
        e.setStatus(EJournalStatus.DRAFT);
        fill(e, form, date);
        if (form.post()) {
            postLines(e);
        }
        return journal.dto(entries.save(e), true);
    }

    /** PUT = full replacement of a DRAFT manual entry; post=true posts it as well. */
    @Transactional
    public JournalEntryDto update(Long id, ManualForm form) {
        JournalEntry e = requireDraft(id);
        LocalDate date = form.date() == null ? e.getEntryDate() : form.date();
        journal.assertOpen(e.getCompanyId(), date);
        fill(e, form, date);
        if (form.post()) {
            postLines(e);
        }
        return journal.dto(entries.save(e), true);
    }

    @Transactional
    public void delete(Long id) {
        entries.delete(requireDraft(id));
    }

    @Transactional
    public JournalEntryDto post(Long id) {
        JournalEntry e = requireDraft(id);
        journal.assertOpen(e.getCompanyId(), e.getEntryDate());
        postLines(e);
        return journal.dto(entries.save(e), true);
    }

    /**
     * Reverses a POSTED entry (manual or automatic) that is not itself a reversal: 400 ACC_069 otherwise (a REVERSED
     * entry cannot be reversed again). The reversal is dated {@code form.date} (default today), not before the
     * original (ACC_071), in an open period (ACC_070).
     */
    @Transactional
    public JournalEntryDto reverse(Long id, ReverseForm form) {
        JournalEntry original = require(id);
        if (original.getStatus() != EJournalStatus.POSTED || original.getReversalOfId() != null) {
            throw ApiException.badRequest(EAccountingError.ACC_069);
        }
        LocalDate date = form == null || form.date() == null ? LocalDate.now(clock) : form.date();
        if (date.isBefore(original.getEntryDate())) {
            throw ApiException.badRequest(EAccountingError.ACC_071, original.getEntryDate().toString());
        }
        Long companyId = original.getCompanyId();
        journal.assertOpen(companyId, date);
        JournalEntry r = new JournalEntry();
        r.setCompanyId(companyId);
        r.setNumber(journal.nextNumber(companyId, date));
        r.setEntryDate(date);
        r.setType(EJournalType.MANUAL);
        r.setStatus(EJournalStatus.POSTED);
        r.setReferenceType(EReferenceType.REVERSAL);
        r.setReference(original.getNumber());
        String description = form == null ? null : JournalService.blankToNull(form.description());
        r.setDescription(description == null ? "Reversal of " + original.getNumber() : description);
        // Unique (company, source) also stops a second reversal racing past the status check.
        r.setSourceType(REVERSAL_SOURCE);
        r.setSourceId(original.getId());
        r.setReversalOfId(original.getId());
        List<Line> swapped = original.getLines().stream()
                .map(l -> new Line(l.getAccountId(), l.getCredit(), l.getDebit(), l.getDescription())).toList();
        JournalService.setLines(r, swapped, new BigDecimal[]{original.getTotalCredit(), original.getTotalDebit()});
        journal.applyBalances(companyId, r.getLines());
        JournalEntry saved = entries.save(r);
        original.setStatus(EJournalStatus.REVERSED);
        original.setReversedById(saved.getId());
        entries.save(original);
        return journal.dto(saved, true);
    }

    private void fill(JournalEntry e, ManualForm form, LocalDate date) {
        List<Line> lines = form.lines().stream()
                .map(l -> new Line(l.accountId(), l.debit(), l.credit(), l.description())).toList();
        BigDecimal[] totals = JournalService.totals(lines);
        if (totals[0].compareTo(totals[1]) != 0) {
            throw ApiException.badRequest(EAccountingError.ACC_066, totals[0].toPlainString(),
                    totals[1].toPlainString(), totals[0].subtract(totals[1]).abs().toPlainString());
        }
        // A draft must already name usable accounts; posting re-checks them under lock.
        for (Long accountId : lines.stream().map(Line::accountId).distinct().toList()) {
            accounts.findById(accountId)
                    .filter(a -> a.isActive() && Objects.equals(a.getCompanyId(), e.getCompanyId()))
                    .orElseThrow(() -> ApiException.badRequest(EAccountingError.ACC_020, accountId));
        }
        e.setEntryDate(date);
        e.setReferenceType(form.referenceType());
        e.setReference(JournalService.blankToNull(form.reference()));
        e.setDescription(JournalService.blankToNull(form.description()));
        JournalService.setLines(e, lines, totals);
    }

    private void postLines(JournalEntry e) {
        journal.applyBalances(e.getCompanyId(), e.getLines());
        e.setStatus(EJournalStatus.POSTED);
    }

    /** A DRAFT manual entry of the caller's company: 404 ACC_021, 400 ACC_068 (automatic), 400 ACC_067 (not draft). */
    private JournalEntry requireDraft(Long id) {
        JournalEntry e = require(id);
        if (e.getType() != EJournalType.MANUAL) {
            throw ApiException.badRequest(EAccountingError.ACC_068);
        }
        if (e.getStatus() != EJournalStatus.DRAFT) {
            throw ApiException.badRequest(EAccountingError.ACC_067);
        }
        return e;
    }

    private JournalEntry require(Long id) {
        JournalEntry e = entries.lockById(id).orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_021));
        TenantGuard.assertSameCompany(e.getCompanyId(), EAccountingError.ACC_021);
        return e;
    }
}
