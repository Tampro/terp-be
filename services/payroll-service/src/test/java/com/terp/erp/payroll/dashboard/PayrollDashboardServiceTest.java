package com.terp.erp.payroll.dashboard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.run.PayrollRun;
import com.terp.erp.payroll.run.PayrollRunRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayrollDashboardServiceTest {
    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollDashboardService service = new PayrollDashboardService(runs,
            Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completedRunsBySumOfPayDateMonth() {
        TestLogin.as(10L, "PAYROLL_VIEW");
        PayrollRun nov = run(LocalDate.of(2026, 11, 5), "100"), nov2 = run(LocalDate.of(2026, 11, 25), "50"),
                oct = run(LocalDate.of(2026, 10, 31), "70");
        when(runs.findByCompanyIdAndStatusAndDeletedFalseAndPayDateBetween(eq(10L), eq(PayrollRun.EStatus.COMPLETED),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)))).thenReturn(List.of(oct, nov, nov2));
        when(runs.findByCompanyIdAndStatusAndDeletedFalseAndPayDateBetween(eq(10L), any(),
                eq(LocalDate.of(2026, 11, 1)), any())).thenReturn(List.of(nov, nov2));
        when(runs.findByCompanyIdAndStatusAndDeletedFalseAndPayDateBetween(eq(10L), any(),
                eq(LocalDate.of(2026, 10, 1)), any())).thenReturn(List.of(oct));

        var d = service.dashboard(null, null);

        assertThat(d.year()).isEqualTo(2026);
        assertThat(d.thisMonthNet()).isEqualByComparingTo("150");
        assertThat(d.lastMonthNet()).isEqualByComparingTo("70");
        assertThat(d.netTrend()).hasSize(12);
        assertThat(d.netTrend().get(10)).isEqualByComparingTo("150");
        assertThat(d.netTrend().get(9)).isEqualByComparingTo("70");
        assertThat(d.netTrend().get(0)).isEqualByComparingTo("0");
    }

    private static PayrollRun run(LocalDate pay, String net) {
        PayrollRun r = new PayrollRun();
        r.setPayDate(pay);
        r.setNetPay(new BigDecimal(net));
        r.setStatus(PayrollRun.EStatus.COMPLETED);
        return r;
    }
}
