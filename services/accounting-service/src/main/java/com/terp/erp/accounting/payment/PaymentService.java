package com.terp.erp.accounting.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.accounting.bank.BankAccount;
import com.terp.erp.accounting.bank.BankAccountService;
import com.terp.erp.accounting.bank.ETransactionType;
import com.terp.erp.accounting.client.CommerceClient;
import com.terp.erp.accounting.client.CommerceClient.ESide;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerService;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.note.AccountNote;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.note.ENoteKind;
import com.terp.erp.accounting.note.ENoteStatus;
import com.terp.erp.accounting.note.NoteService;
import com.terp.erp.accounting.vendor.Vendor;
import com.terp.erp.accounting.vendor.VendorService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Customer payments (CP-) and vendor payments (VP-), ERP manual 12.4 / FR-ACC-PAY-001..005. A payment settles
 * invoices (allocations) with cash through a bank account plus approved credit / debit notes of the same party:
 * cash amount = allocations - notes applied (TODO(assumption): the manual only says "Total"; notes reduce the cash).
 * PENDING → clear → CLEARED | cancel → CANCELLED; only PENDING is deleted.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {
    static final String EVENT_CUSTOMER_PAYMENT_CLEARED = "accounting.customer_payment.cleared";
    static final String EVENT_VENDOR_PAYMENT_CLEARED = "accounting.vendor_payment.cleared";

    public record AllocationForm(
            @Schema(description = "Invoice id of commerce-service (sales invoice for a customer payment, purchase "
                    + "invoice for a vendor payment)", example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Long invoiceId,
            @Schema(description = "Amount for this invoice; at most its balance", example = "500000",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount) {
    }

    public record NoteUseForm(
            @Schema(description = "Approved credit note (customer) or debit note (vendor) id", example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Long noteId,
            @Schema(description = "Amount used; at most the note balance", example = "100000",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount) {
    }

    public record PaymentForm(
            @Schema(description = "Customer id (customer payment) or vendor id (vendor payment)", example = "5",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Long partyId,
            @Schema(description = "Bank account the money goes into / out of; must be active", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Long bankAccountId,
            @Schema(example = "2026-11-22", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate paymentDate,
            @Schema(example = "Transfer 0001") @Size(max = 100) String reference,
            @Schema(example = "November settlement") @Size(max = 5000) String notes,
            @Schema(description = "Invoices settled, 1 to 100", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty @Size(max = 100) List<@Valid @NotNull AllocationForm> allocations,
            @Schema(description = "Credit / debit notes applied, at most 50; their total cannot exceed the allocations")
            @Size(max = 50) List<@Valid @NotNull NoteUseForm> appliedNotes) {
    }

    public record AllocationDto(String invoiceId, String invoiceNumber, BigDecimal amount) {
    }

    public record NoteUseDto(String noteId, BigDecimal amount) {
    }

    public record PaymentDto(String id, String companyId, EPaymentKind kind, String number, LocalDate paymentDate,
                             String partyId, String partyName, String bankAccountId, String reference, String notes,
                             BigDecimal allocatedTotal, BigDecimal notesTotal,
                             @Schema(description = "Cash through the bank account") BigDecimal amount,
                             EPaymentStatus status, String clearedBy, Instant clearedAt, Instant cancelledAt,
                             List<AllocationDto> allocations, List<NoteUseDto> appliedNotes, Instant createdDate) {
        static PaymentDto of(Payment p) {
            return new PaymentDto(p.getId().toString(), p.getCompanyId().toString(), p.getKind(), p.getNumber(),
                    p.getPaymentDate(), p.getPartyId().toString(), p.getPartyName(), p.getBankAccountId().toString(),
                    p.getReference(), p.getNotes(), p.getAllocatedTotal(), p.getNotesTotal(), p.getAmount(),
                    p.getStatus(), p.getClearedBy(), p.getClearedAt(), p.getCancelledAt(),
                    p.getAllocations().stream().map(a -> new AllocationDto(a.getInvoiceId().toString(),
                            a.getInvoiceNumber(), a.getAmount())).toList(),
                    p.getAppliedNotes().stream().map(n -> new NoteUseDto(n.getNoteId().toString(), n.getAmount()))
                            .toList(),
                    p.getCreatedDate());
        }
    }

    /** What the payment form offers: the party's open invoices and its notes with a balance left. */
    public record OpenItems(List<CommerceClient.Invoice> invoices, List<NoteService.NoteDto> notes) {
    }

    private final PaymentRepository payments;
    private final AccountNoteRepository notes;
    private final CommerceClient commerce;
    private final BankAccountService bankAccounts;
    private final CustomerService customers;
    private final VendorService vendors;
    private final DocumentNumbering numbering;
    private final AccountMapping mapping;
    private final JournalService journal;
    private final Clock clock;
    private final OutboxPublisher outbox;

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public Page<PaymentDto> search(EPaymentKind kind, Long requestedCompanyId, Long partyId, EPaymentStatus status,
                                   Long bankAccountId, LocalDate from, LocalDate to, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return payments.search(company, kind, partyId, status, bankAccountId, from, to, pageable).map(PaymentDto::of);
    }

    @Transactional(readOnly = true)
    public PaymentDto get(EPaymentKind kind, Long id) {
        return PaymentDto.of(require(kind, id));
    }

    @Transactional(readOnly = true)
    public OpenItems openItems(EPaymentKind kind, Long requestedCompanyId, Long partyId) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        requireParty(kind, partyId, company);
        ESide side = side(kind);
        List<CommerceClient.Invoice> open = new java.util.ArrayList<>();
        open.addAll(commerce.invoices(side, company, partyId, "POSTED", null, null));
        open.addAll(commerce.invoices(side, company, partyId, "PARTIAL", null, null));
        List<NoteService.NoteDto> available = new java.util.ArrayList<>();
        for (ENoteStatus s : List.of(ENoteStatus.APPROVED, ENoteStatus.PARTIAL)) {
            notes.search(company, noteKind(kind), partyId, s, null, null, Pageable.unpaged())
                    .forEach(n -> available.add(NoteService.NoteDto.of(n)));
        }
        return new OpenItems(open, available);
    }

    // ---------- pending life ----------

    /**
     * PENDING payment. Each invoice is read from commerce-service: same party, POSTED or PARTIAL, amount at most its
     * balance (FR-ACC-PAY-005). Each note: approved, same party, amount at most its balance. Nothing is reserved:
     * balances are checked again when the payment clears (TODO(assumption): no reservation of pending amounts).
     */
    @Transactional
    public PaymentDto create(EPaymentKind kind, Long requestedCompanyId, PaymentForm f) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        String partyName = requireParty(kind, f.partyId(), company);
        BankAccount bank = requireBank(f.bankAccountId(), company);
        if (!bank.isActive()) {
            throw ApiException.badRequest(EAccountingError.ACC_008);
        }
        Payment p = new Payment();
        p.setCompanyId(company);
        p.setKind(kind);
        p.setPartyId(f.partyId());
        p.setPartyName(partyName);
        p.setBankAccountId(bank.getId());
        p.setPaymentDate(f.paymentDate());
        p.setReference(blank(f.reference()));
        p.setNotes(blank(f.notes()));

        Set<Long> seen = new HashSet<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (AllocationForm a : f.allocations()) {
            if (!seen.add(a.invoiceId())) {
                throw ApiException.badRequest(EAccountingError.ACC_055);
            }
            CommerceClient.Invoice inv = commerce.invoice(side(kind), a.invoiceId());
            if (!f.partyId().equals(inv.partyId())) {
                throw ApiException.badRequest(EAccountingError.ACC_052, inv.number());
            }
            if (!inv.open()) {
                throw ApiException.badRequest(EAccountingError.ACC_053, inv.number());
            }
            if (a.amount().compareTo(inv.balance()) > 0) {
                throw ApiException.badRequest(EAccountingError.ACC_054, inv.number(), inv.balance().toPlainString());
            }
            PaymentAllocation row = new PaymentAllocation();
            row.setInvoiceId(a.invoiceId());
            row.setInvoiceNumber(inv.number());
            row.setAmount(a.amount());
            p.getAllocations().add(row);
            allocated = allocated.add(a.amount());
        }
        Set<Long> seenNotes = new HashSet<>();
        BigDecimal noteTotal = BigDecimal.ZERO;
        for (NoteUseForm u : f.appliedNotes() == null ? List.<NoteUseForm>of() : f.appliedNotes()) {
            if (!seenNotes.add(u.noteId())) {
                throw ApiException.badRequest(EAccountingError.ACC_055);
            }
            AccountNote n = notes.findByIdAndDeletedFalse(u.noteId())
                    .filter(x -> x.getCompanyId().equals(company))
                    .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_061));
            checkNote(kind, p, n, u.amount());
            PaymentNote row = new PaymentNote();
            row.setNoteId(n.getId());
            row.setAmount(u.amount());
            p.getAppliedNotes().add(row);
            noteTotal = noteTotal.add(u.amount());
        }
        if (noteTotal.compareTo(allocated) > 0) {
            throw ApiException.badRequest(EAccountingError.ACC_058, noteTotal.toPlainString(),
                    allocated.toPlainString());
        }
        p.setAllocatedTotal(allocated);
        p.setNotesTotal(noteTotal);
        p.setAmount(allocated.subtract(noteTotal));
        p.setNumber(numbering.next(company, kind.prefix(), NoteService.period(f.paymentDate())));
        return PaymentDto.of(payments.save(p));
    }

    @Transactional
    public void delete(EPaymentKind kind, Long id) {
        requirePending(kind, id).setDeleted(true);
    }

    @Transactional
    public PaymentDto cancel(EPaymentKind kind, Long id) {
        Payment p = requirePending(kind, id);
        p.setStatus(EPaymentStatus.CANCELLED);
        p.setCancelledAt(Instant.now(clock));
        return PaymentDto.of(p);
    }

    /**
     * PENDING → CLEARED in one local transaction (FR-ACC-PAY-003): notes' balances drop, the bank account gets a
     * CREDIT (customer) / DEBIT (vendor) transaction of the cash amount, a balanced entry is posted — customer: Dr
     * bank GL / Cr receivable; vendor: Dr payable / Cr bank GL — and the event accounting.customer_payment.cleared /
     * accounting.vendor_payment.cleared (with the allocations) is written to the outbox in the same transaction.
     * commerce-service consumes it and pays each invoice, idempotently per (invoice, paymentId). Before any write,
     * each invoice is read from commerce (synchronous, the caller's token, read-only) and must still be open with a
     * balance covering its allocation (ACC_053 / ACC_054), so a payment that commerce would refuse is not cleared.
     * TODO(assumption): between that read and the consumer, another payment or a return can still shrink a balance;
     *  commerce then refuses the event (dead-lettered to erp.commerce.accounting.dlq for an operator) while the
     *  payment stays CLEARED here — there is no automatic reversal.
     */
    @Transactional
    public PaymentDto clear(EPaymentKind kind, Long id) {
        Payment p = requirePending(kind, id);
        Long company = p.getCompanyId();
        BankAccount bank = requireBank(p.getBankAccountId(), company);
        if (!bank.isActive()) {
            throw ApiException.badRequest(EAccountingError.ACC_008);
        }
        for (PaymentNote u : p.getAppliedNotes()) {
            AccountNote n = notes.lockById(u.getNoteId())
                    .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_061));
            checkNote(kind, p, n, u.getAmount());
            n.setBalance(n.getBalance().subtract(u.getAmount()));
            n.setStatus(n.getBalance().signum() == 0 ? ENoteStatus.APPLIED : ENoteStatus.PARTIAL);
        }
        for (PaymentAllocation a : p.getAllocations()) {
            CommerceClient.Invoice inv = commerce.invoice(side(kind), a.getInvoiceId());
            if (!inv.open()) {
                throw ApiException.badRequest(EAccountingError.ACC_053, inv.number());
            }
            if (a.getAmount().compareTo(inv.balance()) > 0) {
                throw ApiException.badRequest(EAccountingError.ACC_054, inv.number(), inv.balance().toPlainString());
            }
        }
        if (p.getAmount().signum() > 0) {
            String text = (kind == EPaymentKind.CUSTOMER ? "Customer payment " : "Vendor payment ") + p.getNumber()
                    + " - " + p.getPartyName();
            bankAccounts.record(company, bank.getId(),
                    kind == EPaymentKind.CUSTOMER ? ETransactionType.CREDIT : ETransactionType.DEBIT, p.getAmount(),
                    p.getPaymentDate(), kind.sourceType(), p.getId(), p.getNumber(), text);
            // TODO(assumption): a bank account without a GL account posts to the company's BANK system account.
            Long bankGl = bank.getGlAccountId() != null ? bank.getGlAccountId()
                    : mapping.require(company, ESystemAccount.BANK);
            Long party = mapping.require(company,
                    kind == EPaymentKind.CUSTOMER ? ESystemAccount.RECEIVABLE : ESystemAccount.PAYABLE);
            List<JournalService.Line> lines = kind == EPaymentKind.CUSTOMER
                    ? List.of(new JournalService.Line(bankGl, p.getAmount(), BigDecimal.ZERO, text),
                    new JournalService.Line(party, BigDecimal.ZERO, p.getAmount(), text))
                    : List.of(new JournalService.Line(party, p.getAmount(), BigDecimal.ZERO, text),
                    new JournalService.Line(bankGl, BigDecimal.ZERO, p.getAmount(), text));
            journal.post(company, p.getPaymentDate(), kind.sourceType(), p.getId(), p.getNumber(), text, lines);
        }
        p.setStatus(EPaymentStatus.CLEARED);
        p.setClearedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        p.setClearedAt(Instant.now(clock));
        // commerce pays the invoices from "allocations"; notification-service sends INVOICE_PAYMENT_CREATED to the
        // customer's email (the vendor event has no notification route and is ignored there).
        List<java.util.Map<String, Object>> allocations = p.getAllocations().stream()
                .map(a -> Payload.of("invoiceId", a.getInvoiceId(), "amount", a.getAmount())).toList();
        if (kind == EPaymentKind.CUSTOMER) {
            Customer c = customers.require(p.getPartyId());
            outbox.publish(company, EVENT_CUSTOMER_PAYMENT_CLEARED, kind.sourceType(), p.getId(), Payload.of(
                    "paymentId", p.getId(), "number", p.getNumber(), "customerId", p.getPartyId(),
                    "customerName", p.getPartyName(), "amount", p.getAmount(), "paymentDate", p.getPaymentDate(),
                    "invoiceIds", p.getAllocations().stream().map(PaymentAllocation::getInvoiceId).toList(),
                    "allocations", allocations,
                    "recipientEmail", c == null ? null : c.getEmail()));
        } else {
            outbox.publish(company, EVENT_VENDOR_PAYMENT_CLEARED, kind.sourceType(), p.getId(), Payload.of(
                    "paymentId", p.getId(), "number", p.getNumber(), "vendorId", p.getPartyId(),
                    "vendorName", p.getPartyName(), "amount", p.getAmount(), "paymentDate", p.getPaymentDate(),
                    "invoiceIds", p.getAllocations().stream().map(PaymentAllocation::getInvoiceId).toList(),
                    "allocations", allocations,
                    "recipientEmail", vendors.require(p.getPartyId()).getEmail()));
        }
        return PaymentDto.of(p);
    }

    // ---------- helpers ----------

    Payment require(EPaymentKind kind, Long id) {
        Payment p = payments.findByIdAndDeletedFalse(id).filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_050));
        TenantGuard.assertSameCompany(p.getCompanyId(), EAccountingError.ACC_050);
        return p;
    }

    private Payment requirePending(EPaymentKind kind, Long id) {
        Payment p = payments.lockById(id).filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_050));
        TenantGuard.assertSameCompany(p.getCompanyId(), EAccountingError.ACC_050);
        if (p.getStatus() != EPaymentStatus.PENDING) {
            throw ApiException.badRequest(EAccountingError.ACC_051);
        }
        return p;
    }

    /** The party's name; 404 when it is not of the company. */
    private String requireParty(EPaymentKind kind, Long partyId, Long company) {
        if (kind == EPaymentKind.CUSTOMER) {
            Customer c = customers.require(partyId);
            if (!company.equals(c.getCompanyId())) {
                throw ApiException.notFound(EAccountingError.ACC_002);
            }
            return c.getCompanyName();
        }
        Vendor v = vendors.require(partyId);
        if (!company.equals(v.getCompanyId())) {
            throw ApiException.notFound(EAccountingError.ACC_004);
        }
        return v.getCompanyName();
    }

    private BankAccount requireBank(Long id, Long company) {
        BankAccount b = bankAccounts.require(id);
        if (!company.equals(b.getCompanyId())) {
            throw ApiException.notFound(EAccountingError.ACC_006);
        }
        return b;
    }

    private static void checkNote(EPaymentKind kind, Payment p, AccountNote n, BigDecimal amount) {
        boolean usable = n.getKind() == noteKind(kind) && n.getPartyId().equals(p.getPartyId())
                && n.getCompanyId().equals(p.getCompanyId())
                && (n.getStatus() == ENoteStatus.APPROVED || n.getStatus() == ENoteStatus.PARTIAL)
                && n.getBalance().signum() > 0;
        if (!usable) {
            throw ApiException.badRequest(EAccountingError.ACC_056, n.getNumber());
        }
        if (amount.compareTo(n.getBalance()) > 0) {
            throw ApiException.badRequest(EAccountingError.ACC_057, n.getNumber(), n.getBalance().toPlainString());
        }
    }

    static ESide side(EPaymentKind kind) {
        return kind == EPaymentKind.CUSTOMER ? ESide.SALES : ESide.PURCHASE;
    }

    static ENoteKind noteKind(EPaymentKind kind) {
        return kind == EPaymentKind.CUSTOMER ? ENoteKind.CREDIT : ENoteKind.DEBIT;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
