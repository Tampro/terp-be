package com.terp.erp.payroll.payslip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EType;
import com.terp.erp.payroll.run.PayrollEntry;
import com.terp.erp.payroll.run.PayrollEntryRepository;
import com.terp.erp.payroll.run.PayrollRun;
import com.terp.erp.payroll.run.PayrollRunService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayslipServiceTest {
    private static final Instant NOW = Instant.parse("2026-12-01T00:00:00Z");
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final PayrollEntryRepository entries = mock(PayrollEntryRepository.class);
    private final PayrollRunService runs = mock(PayrollRunService.class);
    private final PayslipService service = new PayslipService(payslips, entries, runs, mock(EmployeeClient.class),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final PayrollRun run = new PayrollRun();
    private final List<Payslip> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE");
        run.setId(5L);
        run.setCompanyId(10L);
        run.setTitle("November 2026");
        run.setPeriodStart(LocalDate.of(2026, 11, 1));
        run.setPeriodEnd(LocalDate.of(2026, 11, 30));
        run.setPayDate(LocalDate.of(2026, 12, 5));
        when(runs.require(5L)).thenReturn(run);
        when(payslips.saveAll(anyList())).thenAnswer(i -> {
            saved.addAll(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateOnlyCompletedAndOnlyMissing() {
        assertThatThrownBy(() -> service.generate(5L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EPayrollError.PAY_024));
        run.setStatus(PayrollRun.EStatus.COMPLETED);
        when(entries.findByRunId(5L)).thenReturn(List.of(entry(1L), entry(2L)));
        when(payslips.entriesWithPayslip(any())).thenReturn(Set.of(1L));
        var result = service.generate(5L);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.alreadyGenerated()).isEqualTo(1);
        Payslip p = saved.get(0);
        assertThat(p.getEntryId()).isEqualTo(2L);
        assertThat(p.getPeriodYear()).isEqualTo(2026);
        assertThat(p.getPeriodMonth()).isEqualTo(11);
        assertThat(p.getNetPay()).isEqualByComparingTo("25300000");
        assertThat(p.getStatus()).isEqualTo(Payslip.EStatus.GENERATED);
        assertThat(p.getGeneratedAt()).isEqualTo(NOW);
    }

    @Test
    void pdfMarksDownloaded() {
        Payslip p = new Payslip();
        p.setId(3L);
        p.setCompanyId(10L);
        p.setRunId(5L);
        p.setEntryId(2L);
        p.setEmployeeId(7L);
        p.setEmployeeName("Nguyễn Thử Nghiệm");
        p.setEmployeeNo("EMP000007");
        p.setPeriodYear(2026);
        p.setPeriodMonth(11);
        p.setPeriodStart(run.getPeriodStart());
        p.setPeriodEnd(run.getPeriodEnd());
        p.setPayDate(run.getPayDate());
        p.setNetPay(new BigDecimal("25300000"));
        when(payslips.findById(3L)).thenReturn(Optional.of(p));
        when(runs.requireEntry(2L)).thenReturn(entry(2L));
        var pdf = service.pdf(3L);
        assertThat(new String(pdf.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.fileName()).isEqualTo("payslip-EMP000007-2026-11.pdf");
        assertThat(p.getStatus()).isEqualTo(Payslip.EStatus.DOWNLOADED);
        assertThat(p.getDownloadedAt()).isEqualTo(NOW);
        p.setCompanyId(11L);
        assertThatThrownBy(() -> service.pdf(3L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EPayrollError.PAY_026));
    }

    private static PayrollEntry entry(Long id) {
        PayrollEntry e = new PayrollEntry();
        e.setId(id);
        e.setRunId(5L);
        e.setCompanyId(10L);
        e.setEmployeeId(6L + id);
        e.setEmployeeName("Test " + id);
        e.setBasicSalary(new BigDecimal("20000000"));
        e.setWorkingDays(21);
        e.setPresentDays(new BigDecimal("17.5"));
        e.setLopDays(BigDecimal.ONE);
        e.setTotalEarnings(new BigDecimal("28000000"));
        e.setLopDeduction(new BigDecimal("952381"));
        e.setUnpaidLeaveDeduction(BigDecimal.ZERO);
        e.setOvertimeAmount(BigDecimal.ZERO);
        e.setGrossPay(new BigDecimal("26300000"));
        e.setTotalDeductions(new BigDecimal("1000000"));
        e.setNetPay(new BigDecimal("25300000"));
        e.getLines().add(new PayrollEntry.Line(1L, "Phụ cấp nhà ở", EType.EARNING, ECalculation.PERCENTAGE,
                new BigDecimal("40"), new BigDecimal("8000000")));
        e.getLines().add(new PayrollEntry.Line(3L, "PF", EType.DEDUCTION, ECalculation.FIXED,
                new BigDecimal("1000000"), new BigDecimal("1000000")));
        return e;
    }
}
