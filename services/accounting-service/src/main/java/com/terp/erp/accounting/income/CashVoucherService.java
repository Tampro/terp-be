package com.terp.erp.accounting.income;

import com.terp.erp.accounting.bank.BankAccount;
import com.terp.erp.accounting.bank.BankAccountService;
import com.terp.erp.accounting.bank.ETransactionType;
import com.terp.erp.accounting.coa.AccountCategory;
import com.terp.erp.accounting.coa.CategoryService;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.ECategoryKind;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Revenues and expenses (manual §12.3): DRAFT → approve → APPROVED → post → POSTED. Posting a revenue credits
 * the bank account and journals Dr bank GL / Cr account; an expense debits the bank and journals Dr account /
 * Cr bank GL. Both are idempotent on the voucher (source REVENUE/EXPENSE + id).
 */
@Service
@RequiredArgsConstructor
public class CashVoucherService {
    private final CashVoucherRepository vouchers;
    private final CategoryService categories;
    private final ChartAccountRepository accounts;
    private final BankAccountService bank;
    private final JournalService journal;
    private final DocumentNumbering numbering;
    private final Clock clock;

    public record VoucherForm(
            @Schema(description = "null = today (UTC)", example = "2026-11-22") LocalDate date,
            @Schema(description = "Revenue category for a revenue, expense category for an expense", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long bankAccountId,
            @Schema(description = "Ledger account; null = the category's GL account", example = "40")
            Long accountId,
            @Schema(example = "500000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(example = "INV-EXT-01") @Size(max = 100) String reference,
            @Schema(example = "Consulting fee") @Size(max = 500) String description) {
    }

    public record VoucherDto(String id, String companyId, ECategoryKind kind, String number, LocalDate date,
                             String categoryId, String bankAccountId, String accountId, BigDecimal amount,
                             String reference, String description, EVoucherStatus status, String approvedBy,
                             Instant approvedAt, String journalEntryId, Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<VoucherDto> search(ECategoryKind kind, Long requestedCompanyId, Long categoryId,
                                   EVoucherStatus status, Long bankAccountId, LocalDate from, LocalDate to,
                                   Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return vouchers.search(companyId, kind, categoryId, status, bankAccountId, from, to, pageable)
                .map(v -> dto(v, null));
    }

    @Transactional(readOnly = true)
    public VoucherDto get(ECategoryKind kind, Long id) {
        return dto(require(kind, id), null);
    }

    @Transactional
    public VoucherDto create(ECategoryKind kind, Long requestedCompanyId, VoucherForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        CashVoucher v = new CashVoucher();
        v.setCompanyId(companyId);
        v.setKind(kind);
        apply(v, form);
        v.setNumber(numbering.next(companyId, kind == ECategoryKind.REVENUE ? "REV" : "EXP",
                String.format("%04d-%02d", v.getDate().getYear(), v.getDate().getMonthValue())));
        return dto(vouchers.save(v), null);
    }

    /** Full replacement; DRAFT only (ACC_029). The number keeps its original period. */
    @Transactional
    public VoucherDto update(ECategoryKind kind, Long id, VoucherForm form) {
        CashVoucher v = requireDraft(kind, id);
        apply(v, form);
        return dto(v, null);
    }

    @Transactional
    public void delete(ECategoryKind kind, Long id) {
        requireDraft(kind, id).setDeleted(true);
    }

    @Transactional
    public VoucherDto approve(ECategoryKind kind, Long id) {
        CashVoucher v = requireDraft(kind, id);
        v.setStatus(EVoucherStatus.APPROVED);
        v.setApprovedBy(CurrentUser.get().userId());
        v.setApprovedAt(Instant.now(clock));
        return dto(v, null);
    }

    /** APPROVED only (ACC_030): writes the bank movement and the journal entry, then POSTED. */
    @Transactional
    public VoucherDto post(ECategoryKind kind, Long id) {
        CashVoucher v = require(kind, id);
        if (v.getStatus() != EVoucherStatus.APPROVED) {
            throw ApiException.badRequest(EAccountingError.ACC_030);
        }
        boolean revenue = kind == ECategoryKind.REVENUE;
        String source = revenue ? "REVENUE" : "EXPENSE";
        BankAccount b = bank.require(v.getBankAccountId());
        Long bankGl = bank.glAccountOf(b);
        bank.record(v.getCompanyId(), b.getId(), revenue ? ETransactionType.CREDIT : ETransactionType.DEBIT,
                v.getAmount(), v.getDate(), source, v.getId(), v.getNumber(), v.getDescription());
        List<JournalService.Line> lines = revenue
                ? List.of(new JournalService.Line(bankGl, v.getAmount(), null, null),
                        new JournalService.Line(v.getAccountId(), null, v.getAmount(), null))
                : List.of(new JournalService.Line(v.getAccountId(), v.getAmount(), null, null),
                        new JournalService.Line(bankGl, null, v.getAmount(), null));
        var entry = journal.post(v.getCompanyId(), v.getDate(), source, v.getId(), v.getNumber(),
                v.getDescription(), lines);
        v.setStatus(EVoucherStatus.POSTED);
        return dto(v, entry.id());
    }

    private CashVoucher require(ECategoryKind kind, Long id) {
        EAccountingError notFound = kind == ECategoryKind.REVENUE ? EAccountingError.ACC_027 : EAccountingError.ACC_028;
        CashVoucher v = vouchers.findByIdAndDeletedFalse(id).filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(notFound));
        TenantGuard.assertSameCompany(v.getCompanyId(), notFound);
        return v;
    }

    private CashVoucher requireDraft(ECategoryKind kind, Long id) {
        CashVoucher v = require(kind, id);
        if (v.getStatus() != EVoucherStatus.DRAFT) {
            throw ApiException.badRequest(EAccountingError.ACC_029);
        }
        return v;
    }

    private void apply(CashVoucher v, VoucherForm f) {
        AccountCategory c = categories.require(v.getKind(), f.categoryId());
        if (!Objects.equals(c.getCompanyId(), v.getCompanyId())) {
            throw ApiException.notFound(v.getKind() == ECategoryKind.REVENUE ? EAccountingError.ACC_014
                    : EAccountingError.ACC_015);
        }
        BankAccount b = bank.require(f.bankAccountId());
        if (!Objects.equals(b.getCompanyId(), v.getCompanyId())) {
            throw ApiException.notFound(EAccountingError.ACC_006);
        }
        Long accountId = f.accountId() != null ? f.accountId() : c.getGlAccountId();
        if (accounts.findById(accountId)
                .filter(a -> a.isActive() && Objects.equals(a.getCompanyId(), v.getCompanyId())).isEmpty()) {
            throw ApiException.badRequest(EAccountingError.ACC_020, accountId);
        }
        v.setDate(f.date() == null ? LocalDate.now(clock) : f.date());
        v.setCategoryId(c.getId());
        v.setBankAccountId(b.getId());
        v.setAccountId(accountId);
        v.setAmount(f.amount());
        v.setReference(blankToNull(f.reference()));
        v.setDescription(blankToNull(f.description()));
    }

    private static VoucherDto dto(CashVoucher v, String journalEntryId) {
        return new VoucherDto(v.getId() == null ? null : v.getId().toString(), v.getCompanyId().toString(),
                v.getKind(), v.getNumber(), v.getDate(), v.getCategoryId().toString(),
                v.getBankAccountId().toString(), v.getAccountId().toString(), v.getAmount(), v.getReference(),
                v.getDescription(), v.getStatus(), v.getApprovedBy() == null ? null : v.getApprovedBy().toString(),
                v.getApprovedAt(), journalEntryId, v.getCreatedDate(), v.getUpdatedDate());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
