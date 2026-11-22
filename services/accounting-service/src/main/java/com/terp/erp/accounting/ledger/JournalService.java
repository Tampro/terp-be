package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The general journal. Every posting in this service (revenue, expense, transfer, POS income, payments, notes)
 * writes one balanced entry here in its own transaction, and the entry moves each account's current balance.
 */
@Service
@RequiredArgsConstructor
public class JournalService {
    private final JournalEntryRepository entries;
    private final ChartAccountRepository accounts;
    private final DocumentNumbering numbering;

    /** One side of an entry; null debit/credit = 0. */
    public record Line(Long accountId, BigDecimal debit, BigDecimal credit, String description) {
    }

    public record JournalLineDto(String id, String accountId, String accountCode, String accountName,
                                 String description, BigDecimal debit, BigDecimal credit) {
    }

    /** lines is null in list results; filled by post and by the details endpoint. */
    public record JournalEntryDto(String id, String companyId, String number, LocalDate date, EJournalType type,
                                  String reference, String description, String sourceType, String sourceId,
                                  EJournalStatus status, BigDecimal totalDebit, BigDecimal totalCredit,
                                  List<JournalLineDto> lines, Instant createdDate) {
    }

    /**
     * Writes an AUTOMATIC, POSTED entry for a source document in the caller's transaction. Idempotent on
     * (company, sourceType, sourceId): a repeat returns the existing entry and changes no balance. Refuses an
     * empty entry or a line without exactly one positive side (ACC_019), an unbalanced entry (ACC_018) and an
     * account of another company or inactive (ACC_020).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntryDto post(Long companyId, LocalDate date, String sourceType, Long sourceId, String reference,
                                String description, List<Line> lines) {
        Optional<JournalEntry> existing = entries.findByCompanyIdAndSourceTypeAndSourceId(companyId, sourceType,
                sourceId);
        if (existing.isPresent()) {
            return dto(existing.get(), true);
        }
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest(EAccountingError.ACC_019);
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Line l : lines) {
            BigDecimal d = orZero(l.debit());
            BigDecimal c = orZero(l.credit());
            boolean oneSide = (d.signum() > 0 && c.signum() == 0) || (c.signum() > 0 && d.signum() == 0);
            if (l.accountId() == null || !oneSide) {
                throw ApiException.badRequest(EAccountingError.ACC_019);
            }
            totalDebit = totalDebit.add(d);
            totalCredit = totalCredit.add(c);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw ApiException.badRequest(EAccountingError.ACC_018);
        }
        // Lock in id order so two postings touching the same accounts cannot deadlock.
        Map<Long, ChartAccount> locked = new HashMap<>();
        for (Long id : new TreeSet<>(lines.stream().map(Line::accountId).toList())) {
            ChartAccount a = accounts.lockById(id)
                    .filter(x -> x.isActive() && Objects.equals(x.getCompanyId(), companyId))
                    .orElseThrow(() -> ApiException.badRequest(EAccountingError.ACC_020, id));
            locked.put(id, a);
        }
        JournalEntry e = new JournalEntry();
        e.setCompanyId(companyId);
        e.setNumber(numbering.next(companyId, "JE", String.valueOf(date.getYear())));
        e.setEntryDate(date);
        e.setType(EJournalType.AUTOMATIC);
        e.setStatus(EJournalStatus.POSTED);
        e.setReference(blankToNull(reference));
        e.setDescription(blankToNull(description));
        e.setSourceType(sourceType);
        e.setSourceId(sourceId);
        e.setTotalDebit(totalDebit);
        e.setTotalCredit(totalCredit);
        for (Line l : lines) {
            JournalLine row = new JournalLine();
            row.setAccountId(l.accountId());
            row.setDebit(orZero(l.debit()));
            row.setCredit(orZero(l.credit()));
            row.setDescription(blankToNull(l.description()));
            e.getLines().add(row);
            ChartAccount a = locked.get(l.accountId());
            BigDecimal change = a.getNormalBalance() == ENormalBalance.DEBIT
                    ? row.getDebit().subtract(row.getCredit())
                    : row.getCredit().subtract(row.getDebit());
            a.setCurrentBalance(a.getCurrentBalance().add(change));
        }
        return dto(entries.save(e), true);
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryDto> search(Long requestedCompanyId, String sourceType, LocalDate from, LocalDate to,
                                        Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return entries.search(companyId, blankToNull(sourceType), from, to, pageable).map(e -> dto(e, false));
    }

    @Transactional(readOnly = true)
    public JournalEntryDto get(Long id) {
        JournalEntry e = entries.findById(id).orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_021));
        TenantGuard.assertSameCompany(e.getCompanyId(), EAccountingError.ACC_021);
        return dto(e, true);
    }

    private JournalEntryDto dto(JournalEntry e, boolean withLines) {
        List<JournalLineDto> lines = null;
        if (withLines) {
            Map<Long, ChartAccount> byId = new HashMap<>();
            accounts.findAllById(e.getLines().stream().map(JournalLine::getAccountId).distinct().toList())
                    .forEach(a -> byId.put(a.getId(), a));
            lines = e.getLines().stream().map(l -> {
                ChartAccount a = byId.get(l.getAccountId());
                return new JournalLineDto(l.getId() == null ? null : l.getId().toString(),
                        l.getAccountId().toString(), a == null ? null : a.getCode(), a == null ? null : a.getName(),
                        l.getDescription(), l.getDebit(), l.getCredit());
            }).toList();
        }
        return new JournalEntryDto(e.getId() == null ? null : e.getId().toString(), e.getCompanyId().toString(),
                e.getNumber(), e.getEntryDate(), e.getType(), e.getReference(), e.getDescription(),
                e.getSourceType(), e.getSourceId() == null ? null : e.getSourceId().toString(), e.getStatus(),
                e.getTotalDebit(), e.getTotalCredit(), lines, e.getCreatedDate());
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
