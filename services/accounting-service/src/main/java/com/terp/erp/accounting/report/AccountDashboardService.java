package com.terp.erp.accounting.report;

import com.terp.erp.accounting.coa.ECategoryKind;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.income.CashVoucher;
import com.terp.erp.accounting.income.CashVoucherRepository;
import com.terp.erp.accounting.income.EVoucherStatus;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.vendor.VendorRepository;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Account dashboard (ERP manual 5.2): money of one calendar year, party counts, recent vouchers. */
@Service
@RequiredArgsConstructor
public class AccountDashboardService {
    private final CashVoucherRepository vouchers;
    private final PaymentRepository payments;
    private final CustomerRepository customers;
    private final VendorRepository vendors;
    private final Clock clock;

    public record MonthFlow(int month, BigDecimal moneyIn, BigDecimal moneyOut) {
    }

    public record RecentVoucher(String id, String number, LocalDate date, BigDecimal amount, EVoucherStatus status,
                                String description) {
        static RecentVoucher of(CashVoucher v) {
            return new RecentVoucher(String.valueOf(v.getId()), v.getNumber(), v.getDate(), v.getAmount(),
                    v.getStatus(), v.getDescription());
        }
    }

    @Schema(description = "Money figures are of the year (POSTED vouchers, CLEARED payments); counts are current")
    public record AccountDashboard(int year, BigDecimal totalRevenue, BigDecimal totalExpense,
                                   BigDecimal customerPayments, BigDecimal vendorPayments,
                                   @Schema(description = "(revenue + customer payments) - (expense + vendor payments)")
                                   BigDecimal netCashFlow, long clients, long vendors, List<MonthFlow> monthly,
                                   List<RecentVoucher> recentRevenues, List<RecentVoucher> recentExpenses) {
    }

    @Transactional(readOnly = true)
    public AccountDashboard dashboard(Long companyId, Integer year) {
        Long company = TenantGuard.resolveCompany(companyId, EAccountingError.ACC_001);
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);
        Map<Integer, BigDecimal> rev = byMonth(vouchers.postedByMonth(company, ECategoryKind.REVENUE, from, to));
        Map<Integer, BigDecimal> exp = byMonth(vouchers.postedByMonth(company, ECategoryKind.EXPENSE, from, to));
        Map<Integer, BigDecimal> cin = byMonth(payments.clearedByMonth(company, EPaymentKind.CUSTOMER, null, from, to));
        Map<Integer, BigDecimal> out = byMonth(payments.clearedByMonth(company, EPaymentKind.VENDOR, null, from, to));
        List<MonthFlow> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthly.add(new MonthFlow(m, get(rev, m).add(get(cin, m)), get(exp, m).add(get(out, m))));
        }
        BigDecimal r = sum(rev);
        BigDecimal e = sum(exp);
        BigDecimal c = sum(cin);
        BigDecimal v = sum(out);
        // TODO(assumption): net cash flow = (revenue + customer payments) - (expense + vendor payments); confirm with
        // the chief accountant whether bank transfers or POS income should count too.
        return new AccountDashboard(y, r, e, c, v, r.add(c).subtract(e.add(v)),
                customers.countByCompanyIdAndDeletedFalse(company), vendors.countByCompanyIdAndDeletedFalse(company),
                monthly, recent(company, ECategoryKind.REVENUE), recent(company, ECategoryKind.EXPENSE));
    }

    private List<RecentVoucher> recent(Long company, ECategoryKind kind) {
        return vouchers.findTop5ByCompanyIdAndKindAndDeletedFalseOrderByDateDescIdDesc(company, kind).stream()
                .map(RecentVoucher::of).toList();
    }

    /** [month, sum] rows to month -> sum. */
    public static Map<Integer, BigDecimal> byMonth(List<Object[]> rows) {
        Map<Integer, BigDecimal> out = new HashMap<>();
        for (Object[] r : rows) {
            out.put(((Number) r[0]).intValue(), r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1]);
        }
        return out;
    }

    private static BigDecimal get(Map<Integer, BigDecimal> m, int month) {
        return m.getOrDefault(month, BigDecimal.ZERO);
    }

    private static BigDecimal sum(Map<Integer, BigDecimal> m) {
        return m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
