package com.terp.erp.accounting.payroll;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.bank.BankAccount;
import com.terp.erp.accounting.bank.BankAccountService;
import com.terp.erp.accounting.bank.ETransactionType;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.payroll.PayrollPostingService.PayrollPostingForm;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollPostingServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 12, 5);

    private final BankAccountService banks = mock(BankAccountService.class);
    private final JournalService journal = mock(JournalService.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final PayrollPostingService service = new PayrollPostingService(banks, journal, mapping,
            Clock.fixed(Instant.parse("2026-12-05T08:00:00Z"), ZoneOffset.UTC));
    private final BankAccount bank = new BankAccount();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_PAY");
        bank.setId(3L);
        bank.setCompanyId(10L);
        when(banks.require(3L)).thenReturn(bank);
        when(banks.glAccountOf(bank)).thenReturn(112L);
        when(mapping.require(10L, ESystemAccount.SALARY_PAYABLE)).thenReturn(334L);
        when(journal.post(eq(10L), any(), any(), any(), any(), any(), anyList()))
                .thenReturn(new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null, null, null,
                        "PAYROLL_RUN", "12", null, null, null, List.of(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesABankDebitAndABalancedSalaryPayableEntryKeyedOnTheRun() {
        var dto = service.post(null, form());

        verify(banks).record(10L, 3L, ETransactionType.DEBIT, new BigDecimal("9500000"), DAY, "PAYROLL_RUN", 12L,
                "PAYROLL-12", "Salaries November");
        ArgumentCaptor<List<JournalService.Line>> lines = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq("PAYROLL_RUN"), eq(12L), eq("PAYROLL-12"),
                eq("Salaries November"), lines.capture());
        List<JournalService.Line> l = lines.getValue();
        assertThat(l).hasSize(2);
        assertThat(l.get(0).accountId()).isEqualTo(334L);
        assertThat(l.get(0).debit()).isEqualByComparingTo("9500000");
        assertThat(l.get(1).accountId()).isEqualTo(112L);
        assertThat(l.get(1).credit()).isEqualByComparingTo("9500000");
        assertThat(dto.journalNumber()).isEqualTo("JE-2026-001");
    }

    @Test
    void repeatUsesTheSameSourceKeySoBothCallsAreIdempotent() {
        service.post(null, form());
        service.post(null, form());
        // BankAccountService.record and JournalService.post return the existing row on the same
        // (company, PAYROLL_RUN, runId); both calls carry exactly that key
        verify(banks, org.mockito.Mockito.times(2)).record(eq(10L), eq(3L), eq(ETransactionType.DEBIT), any(),
                any(), eq("PAYROLL_RUN"), eq(12L), any(), any());
        verify(journal, org.mockito.Mockito.times(2)).post(eq(10L), any(), eq("PAYROLL_RUN"), eq(12L), any(), any(),
                anyList());
    }

    @Test
    void otherCompanysBankAccountIsNotFound() {
        bank.setCompanyId(11L);
        assertThatThrownBy(() -> service.post(null, form())).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EAccountingError.ACC_006));
        verify(banks, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static PayrollPostingForm form() {
        return new PayrollPostingForm(12L, 3L, new BigDecimal("9500000"), DAY, "PAYROLL-12", "Salaries November");
    }

    @Test
    @SuppressWarnings("unchecked")
    void accruesTheSalaryExpenseSoSalaryPayableNetsToZero() {
        when(mapping.require(10L, ESystemAccount.SALARY_EXPENSE)).thenReturn(6421L);
        service.post(null, new PayrollPostingForm(12L, 3L, new BigDecimal("9500000"), DAY, "PAYROLL-12",
                "Salaries November", LocalDate.of(2026, 11, 30)));

        ArgumentCaptor<List<JournalService.Line>> accrual = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(LocalDate.of(2026, 11, 30)), eq("PAYROLL_ACCRUAL"), eq(12L),
                eq("PAYROLL-12"), any(), accrual.capture());
        ArgumentCaptor<List<JournalService.Line>> payment = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq("PAYROLL_RUN"), eq(12L), any(), any(), payment.capture());
        assertThat(accrual.getValue().get(0).accountId()).isEqualTo(6421L);
        assertThat(accrual.getValue().get(0).debit()).isEqualByComparingTo("9500000");
        assertThat(accrual.getValue().get(1).accountId()).isEqualTo(334L);
        assertThat(accrual.getValue().get(1).credit()).isEqualByComparingTo("9500000");
        // 334: credit 9.5M (accrual) - debit 9.5M (payment) = 0
        assertThat(payment.getValue().get(0).accountId()).isEqualTo(334L);
        assertThat(payment.getValue().get(0).debit()).isEqualByComparingTo(accrual.getValue().get(1).credit());
    }
}
