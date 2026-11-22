package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Net movement of accounts over a date range, read from counted journal lines (POSTED and REVERSED: a reversed
 * entry and its reversal cancel each other; drafts never count). Used by goals and budgets.
 */
@Component
@RequiredArgsConstructor
public class AccountMovement {
    private final JournalEntryRepository entries;
    private final ChartAccountRepository accounts;

    /** Σ debit − Σ credit of the accounts' counted lines dated from..to inclusive; 0 when from > to. */
    public BigDecimal debitMinusCredit(Long companyId, Collection<Long> accountIds, java.time.LocalDate from,
                                       java.time.LocalDate to) {
        if (accountIds.isEmpty() || from.isAfter(to)) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = BigDecimal.ZERO;
        for (AccountSum s : entries.sumsBetween(companyId, accountIds, JournalService.COUNTED, from, to)) {
            net = net.add(nz(s.debit())).subtract(nz(s.credit()));
        }
        return net;
    }

    /** The account and every account below it in the company's chart (parent_id tree). */
    public Set<Long> withDescendants(Long companyId, Long accountId) {
        List<ChartAccount> chart = accounts.findByCompanyIdOrderByCode(companyId);
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> todo = new ArrayDeque<>();
        todo.push(accountId);
        while (!todo.isEmpty()) {
            Long id = todo.pop();
            if (result.add(id)) {
                for (ChartAccount a : chart) {
                    if (Objects.equals(a.getParentId(), id)) {
                        todo.push(a.getId());
                    }
                }
            }
        }
        return result;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
