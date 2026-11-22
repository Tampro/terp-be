package com.terp.erp.accounting.note;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.accounting.client.CommerceClient;
import com.terp.erp.accounting.client.CommerceClient.ESide;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.CustomerService;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.vendor.VendorService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Credit notes (CN-, from a completed sales return) and debit notes (DN-, from a completed purchase return), ERP
 * manual 12.5. One live note per return; total = the return total read from commerce-service. Approve posts the
 * journal entry; the balance is used up by cleared payments (PaymentService).
 */
@Service
@RequiredArgsConstructor
public class NoteService {
    public record NoteForm(
            @Schema(description = "Sales return id (credit note) or purchase return id (debit note) of "
                    + "commerce-service; must be COMPLETED", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Long returnId,
            @Schema(description = "Note date; omit = the return date", example = "2026-11-22") LocalDate noteDate,
            @Schema(description = "Notes", example = "Damaged goods") @Size(max = 5000) String notes) {
    }

    public record NoteDto(String id, String companyId, ENoteKind kind, String number, String returnId,
                          String returnNumber, String partyId, String partyName, LocalDate noteDate, BigDecimal total,
                          BigDecimal taxAmount, BigDecimal balance, ENoteStatus status, String approvedBy,
                          Instant approvedAt, String notes, Instant createdDate) {
        public static NoteDto of(AccountNote n) {
            return new NoteDto(n.getId().toString(), n.getCompanyId().toString(), n.getKind(), n.getNumber(),
                    n.getReturnId().toString(), n.getReturnNumber(), n.getPartyId().toString(), n.getPartyName(),
                    n.getNoteDate(), n.getTotal(), n.getTaxAmount(), n.getBalance(), n.getStatus(),
                    n.getApprovedBy(), n.getApprovedAt(), n.getNotes(), n.getCreatedDate());
        }
    }

    private final AccountNoteRepository notes;
    private final CommerceClient commerce;
    private final CustomerService customers;
    private final VendorService vendors;
    private final DocumentNumbering numbering;
    private final AccountMapping mapping;
    private final JournalService journal;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<NoteDto> search(ENoteKind kind, Long requestedCompanyId, Long partyId, ENoteStatus status,
                                LocalDate from, LocalDate to, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return notes.search(company, kind, partyId, status, from, to, pageable).map(NoteDto::of);
    }

    @Transactional(readOnly = true)
    public NoteDto get(ENoteKind kind, Long id) {
        return NoteDto.of(require(kind, id));
    }

    @Transactional
    public NoteDto create(ENoteKind kind, Long requestedCompanyId, NoteForm f) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        CommerceClient.ReturnDoc r = commerce.returnDoc(side(kind), f.returnId());
        // commerce checks the caller's company; a super admin is not bound to one, so the party decides.
        Long partyCompany = kind == ENoteKind.CREDIT ? customers.require(r.partyId()).getCompanyId()
                : vendors.require(r.partyId()).getCompanyId();
        if (!company.equals(partyCompany)) {
            throw ApiException.notFound(EAccountingError.ACC_061);
        }
        if (!"COMPLETED".equals(r.status())) {
            throw ApiException.badRequest(EAccountingError.ACC_063, r.number());
        }
        notes.findByCompanyIdAndKindAndReturnKey(company, kind, r.id()).ifPresent(n -> {
            throw ApiException.badRequest(EAccountingError.ACC_064, r.number(), n.getNumber());
        });
        AccountNote n = new AccountNote();
        n.setCompanyId(company);
        n.setKind(kind);
        n.setReturnId(r.id());
        n.setReturnKey(r.id());
        n.setReturnNumber(r.number());
        n.setPartyId(r.partyId());
        n.setPartyName(r.partyName());
        n.setNoteDate(f.noteDate() != null ? f.noteDate() : r.date() != null ? r.date() : LocalDate.now(clock));
        n.setTotal(r.total());
        n.setTaxAmount(r.tax() == null ? BigDecimal.ZERO : r.tax());
        n.setBalance(r.total());
        n.setNotes(f.notes() == null || f.notes().isBlank() ? null : f.notes().trim());
        n.setNumber(numbering.next(company, kind.prefix(), period(n.getNoteDate())));
        return NoteDto.of(notes.save(n));
    }

    /**
     * DRAFT → APPROVED and the journal entry, idempotent on (note). Credit note: Dr sales revenue (net) and output
     * VAT (tax) / Cr receivable (total). Debit note: Dr payable (total) / Cr inventory (net) and input VAT (tax).
     */
    @Transactional
    public NoteDto approve(ENoteKind kind, Long id) {
        AccountNote n = requireLocked(kind, id);
        if (n.getStatus() != ENoteStatus.DRAFT) {
            throw ApiException.badRequest(EAccountingError.ACC_062);
        }
        BigDecimal tax = n.getTaxAmount();
        BigDecimal net = n.getTotal().subtract(tax);
        Long c = n.getCompanyId();
        String text = (kind == ENoteKind.CREDIT ? "Credit note " : "Debit note ") + n.getNumber();
        List<JournalService.Line> lines = new ArrayList<>();
        // TODO(assumption): accounts of a return (5111 or 5213 sales returns, 156 or 632 for goods sent back) and
        //  the VAT split come from the chief accountant; until then revenue / inventory carry the net amount.
        if (kind == ENoteKind.CREDIT) {
            lines.add(new JournalService.Line(mapping.require(c, ESystemAccount.SALES_REVENUE), net, BigDecimal.ZERO,
                    text));
            if (tax.signum() > 0) {
                lines.add(new JournalService.Line(mapping.require(c, ESystemAccount.OUTPUT_VAT), tax, BigDecimal.ZERO,
                        text));
            }
            lines.add(new JournalService.Line(mapping.require(c, ESystemAccount.RECEIVABLE), BigDecimal.ZERO,
                    n.getTotal(), text));
        } else {
            lines.add(new JournalService.Line(mapping.require(c, ESystemAccount.PAYABLE), n.getTotal(),
                    BigDecimal.ZERO, text));
            lines.add(new JournalService.Line(mapping.require(c, ESystemAccount.INVENTORY), BigDecimal.ZERO, net,
                    text));
            if (tax.signum() > 0) {
                lines.add(new JournalService.Line(mapping.require(c, ESystemAccount.INPUT_VAT), BigDecimal.ZERO, tax,
                        text));
            }
        }
        journal.post(c, n.getNoteDate(), kind.sourceType(), n.getId(), n.getNumber(), text, lines);
        n.setStatus(ENoteStatus.APPROVED);
        n.setApprovedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        n.setApprovedAt(Instant.now(clock));
        return NoteDto.of(n);
    }

    /** A draft only; the return is free for a new note afterwards. */
    @Transactional
    public void delete(ENoteKind kind, Long id) {
        AccountNote n = requireLocked(kind, id);
        if (n.getStatus() != ENoteStatus.DRAFT) {
            throw ApiException.badRequest(EAccountingError.ACC_062);
        }
        n.setDeleted(true);
        n.setReturnKey(null);
    }

    AccountNote require(ENoteKind kind, Long id) {
        AccountNote n = notes.findByIdAndDeletedFalse(id).filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_061));
        TenantGuard.assertSameCompany(n.getCompanyId(), EAccountingError.ACC_061);
        return n;
    }

    private AccountNote requireLocked(ENoteKind kind, Long id) {
        AccountNote n = notes.lockById(id).filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_061));
        TenantGuard.assertSameCompany(n.getCompanyId(), EAccountingError.ACC_061);
        return n;
    }

    static ESide side(ENoteKind kind) {
        return kind == ENoteKind.CREDIT ? ESide.SALES : ESide.PURCHASE;
    }

    public static String period(LocalDate d) {
        return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
    }
}
