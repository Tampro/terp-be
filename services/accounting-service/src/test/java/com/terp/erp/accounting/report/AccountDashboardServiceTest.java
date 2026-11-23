package com.terp.erp.accounting.report;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.coa.ECategoryKind;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.income.CashVoucher;
import com.terp.erp.accounting.income.CashVoucherRepository;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.vendor.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDashboardServiceTest {
    private final CashVoucherRepository vouchers = mock(CashVoucherRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final AccountDashboardService service = new AccountDashboardService(vouchers, payments, customers, vendors,
            Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static List<Object[]> rows(Object[]... r) {
        return List.of(r);
    }

    @Test
    void figuresOfTheCallersCompanyAndYear() {
        TestLogin.as(10L, "ACCOUNTING_REPORT_VIEW");
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        when(vouchers.postedByMonth(10L, ECategoryKind.REVENUE, from, to))
                .thenReturn(rows(new Object[]{1, new BigDecimal("100")}, new Object[]{3, new BigDecimal("50")}));
        when(vouchers.postedByMonth(10L, ECategoryKind.EXPENSE, from, to))
                .thenReturn(rows(new Object[]{1, new BigDecimal("30")}));
        when(payments.clearedByMonth(10L, EPaymentKind.CUSTOMER, null, from, to))
                .thenReturn(rows(new Object[]{3, new BigDecimal("200")}));
        when(payments.clearedByMonth(10L, EPaymentKind.VENDOR, null, from, to))
                .thenReturn(rows(new Object[]{12, new BigDecimal("70")}));
        when(customers.countByCompanyIdAndDeletedFalse(10L)).thenReturn(4L);
        when(vendors.countByCompanyIdAndDeletedFalse(10L)).thenReturn(2L);
        CashVoucher v = new CashVoucher();
        v.setId(9L);
        v.setNumber("REV-0009");
        v.setAmount(BigDecimal.TEN);
        when(vouchers.findTop5ByCompanyIdAndKindAndDeletedFalseOrderByDateDescIdDesc(10L, ECategoryKind.REVENUE))
                .thenReturn(List.of(v));

        var d = service.dashboard(99L, null);

        assertThat(d.year()).isEqualTo(2026);
        assertThat(d.totalRevenue()).isEqualByComparingTo("150");
        assertThat(d.totalExpense()).isEqualByComparingTo("30");
        assertThat(d.customerPayments()).isEqualByComparingTo("200");
        assertThat(d.vendorPayments()).isEqualByComparingTo("70");
        assertThat(d.netCashFlow()).isEqualByComparingTo("250");
        assertThat(d.clients()).isEqualTo(4);
        assertThat(d.vendors()).isEqualTo(2);
        assertThat(d.monthly()).hasSize(12);
        assertThat(d.monthly().get(0).moneyIn()).isEqualByComparingTo("100");
        assertThat(d.monthly().get(0).moneyOut()).isEqualByComparingTo("30");
        assertThat(d.monthly().get(2).moneyIn()).isEqualByComparingTo("250");
        assertThat(d.monthly().get(11).moneyOut()).isEqualByComparingTo("70");
        assertThat(d.recentRevenues()).extracting(AccountDashboardService.RecentVoucher::id).containsExactly("9");
        assertThat(d.recentExpenses()).isEmpty();
        // The requested company 99 is ignored for a company user.
        verify(customers, never()).countByCompanyIdAndDeletedFalse(99L);
        verify(payments, never()).clearedByMonth(eq(99L), any(), isNull(), any(), any());
    }

    @Test
    void handlerNeedsTheReportPermission() throws Exception {
        var m = ReportController.class.getMethod("dashboard", Long.class, Integer.class);
        assertThat(m.getAnnotation(PreAuthorize.class).value()).contains("ACCOUNTING_REPORT_VIEW");
    }
}
