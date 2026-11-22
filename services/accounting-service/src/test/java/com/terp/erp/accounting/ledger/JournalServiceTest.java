package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);

    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final DocumentNumbering numbering = mock(DocumentNumbering.class);
    private final JournalService service = new JournalService(entries, accounts, numbering,
            mock(FiscalYearCloseRepository.class));
    private final ChartAccount bank = account(1L, 10L, ENormalBalance.DEBIT, "100");
    private final ChartAccount revenue = account(2L, 10L, ENormalBalance.CREDIT, "0");

    @BeforeEach
    void setUp() {
        when(accounts.lockById(1L)).thenReturn(Optional.of(bank));
        when(accounts.lockById(2L)).thenReturn(Optional.of(revenue));
        when(accounts.findAllById(any())).thenReturn(List.of(bank, revenue));
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(eq(10L), anyString(), any())).thenReturn(Optional.empty());
        when(numbering.next(10L, "JE", "2026")).thenReturn("JE-2026-001");
        when(entries.save(any())).thenAnswer(i -> {
            JournalEntry e = i.getArgument(0);
            e.setId(50L);
            return e;
        });
    }

    @Test
    void unbalancedEntryRefused() {
        assertCode(() -> service.post(10L, DAY, "REVENUE", 1L, null, null, List.of(
                new JournalService.Line(1L, new BigDecimal("100"), null, null),
                new JournalService.Line(2L, null, new BigDecimal("90"), null))), EAccountingError.ACC_018);
        verify(entries, never()).save(any());
        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("100");
    }

    @Test
    void emptyOrTwoSidedLinesRefused() {
        assertCode(() -> service.post(10L, DAY, "REVENUE", 1L, null, null, List.of()), EAccountingError.ACC_019);
        assertCode(() -> service.post(10L, DAY, "REVENUE", 1L, null, null, List.of(
                new JournalService.Line(1L, BigDecimal.TEN, BigDecimal.TEN, null))), EAccountingError.ACC_019);
    }

    @Test
    void balancesMoveByNormalSide() {
        var dto = service.post(10L, DAY, "REVENUE", 1L, "REV-1", "Fee", List.of(
                new JournalService.Line(1L, new BigDecimal("40"), null, null),
                new JournalService.Line(2L, null, new BigDecimal("40"), null)));
        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("140");
        assertThat(revenue.getCurrentBalance()).isEqualByComparingTo("40");
        assertThat(dto.number()).isEqualTo("JE-2026-001");
        assertThat(dto.status()).isEqualTo(EJournalStatus.POSTED);
        assertThat(dto.totalDebit()).isEqualByComparingTo(dto.totalCredit());
        assertThat(dto.lines()).hasSize(2);

        // A credit on a debit-normal account lowers it; a debit on a credit-normal account lowers it.
        service.post(10L, DAY, "REVENUE", 2L, null, null, List.of(
                new JournalService.Line(2L, new BigDecimal("15"), null, null),
                new JournalService.Line(1L, null, new BigDecimal("15"), null)));
        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("125");
        assertThat(revenue.getCurrentBalance()).isEqualByComparingTo("25");
    }

    @Test
    void sameSourceIsPostedOnce() {
        JournalEntry existing = new JournalEntry();
        existing.setId(7L);
        existing.setCompanyId(10L);
        existing.setNumber("JE-2026-001");
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(10L, "REVENUE", 1L)).thenReturn(Optional.of(existing));
        var dto = service.post(10L, DAY, "REVENUE", 1L, null, null, List.of(
                new JournalService.Line(1L, new BigDecimal("40"), null, null),
                new JournalService.Line(2L, null, new BigDecimal("40"), null)));
        assertThat(dto.id()).isEqualTo("7");
        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("100");
        verify(entries, never()).save(any());
    }

    @Test
    void otherCompanyOrInactiveAccountRefused() {
        revenue.setCompanyId(11L);
        assertCode(() -> service.post(10L, DAY, "REVENUE", 1L, null, null, List.of(
                new JournalService.Line(1L, BigDecimal.TEN, null, null),
                new JournalService.Line(2L, null, BigDecimal.TEN, null))), EAccountingError.ACC_020);
        revenue.setCompanyId(10L);
        revenue.setActive(false);
        assertCode(() -> service.post(10L, DAY, "REVENUE", 1L, null, null, List.of(
                new JournalService.Line(1L, BigDecimal.TEN, null, null),
                new JournalService.Line(2L, null, BigDecimal.TEN, null))), EAccountingError.ACC_020);
        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("100");
    }

    private static ChartAccount account(Long id, Long companyId, ENormalBalance normal, String balance) {
        ChartAccount a = new ChartAccount();
        a.setId(id);
        a.setCompanyId(companyId);
        a.setCode("A" + id);
        a.setName("Account " + id);
        a.setNormalBalance(normal);
        a.setCurrentBalance(new BigDecimal(balance));
        return a;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
