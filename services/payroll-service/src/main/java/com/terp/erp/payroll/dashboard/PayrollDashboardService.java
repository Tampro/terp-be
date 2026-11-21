package com.terp.erp.payroll.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.run.PayrollRun;
import com.terp.erp.payroll.run.PayrollRunRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Company dashboard figures of payroll (docs/analysis/06 §5.1: "Payroll This Month" card, "Payroll Trend" chart).
 * TODO(assumption): only COMPLETED runs count (DRAFT/PROCESSING are not final, CANCELLED never paid), by pay date.
 */
@Service
@RequiredArgsConstructor
public class PayrollDashboardService {
    private final PayrollRunRepository runs;
    private final Clock clock;

    /** currency: null = the company currency (not stored by payroll-service). netTrend: 12 values, Jan..Dec. */
    public record PayrollDashboard(String currency, BigDecimal thisMonthNet, BigDecimal lastMonthNet, int year,
                                   List<BigDecimal> netTrend) {
    }

    @Transactional(readOnly = true)
    public PayrollDashboard dashboard(Long requestedCompanyId, Integer year) {
        Long companyId = Texts.company(requestedCompanyId);
        YearMonth now = YearMonth.now(clock);
        int y = year == null ? now.getYear() : year;
        List<BigDecimal> trend = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            trend.add(BigDecimal.ZERO);
        }
        for (PayrollRun r : completed(companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))) {
            int m = r.getPayDate().getMonthValue() - 1;
            trend.set(m, trend.get(m).add(r.getNetPay()));
        }
        return new PayrollDashboard(null, net(companyId, now), net(companyId, now.minusMonths(1)), y, trend);
    }

    private BigDecimal net(Long companyId, YearMonth m) {
        return completed(companyId, m.atDay(1), m.atEndOfMonth()).stream().map(PayrollRun::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PayrollRun> completed(Long companyId, LocalDate from, LocalDate to) {
        return runs.findByCompanyIdAndStatusAndDeletedFalseAndPayDateBetween(companyId, PayrollRun.EStatus.COMPLETED,
                from, to);
    }
}
