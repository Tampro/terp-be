package com.terp.erp.accounting.report;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.report.ReportService.BalanceRow;
import com.terp.erp.accounting.report.ReportService.EParty;
import com.terp.erp.accounting.report.ReportService.TaxSummary;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tax summary and balance PDFs: same service call (tenant resolution inside it) and permission as the JSON view. */
class ReportPdfEndpointsTest {
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    private final ReportService service = mock(ReportService.class);
    private final ReportController controller = new ReportController(service, mock(AccountDashboardService.class));

    private static void assertPdf(ResponseEntity<byte[]> r) {
        assertThat(r.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(r.getBody(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void taxSummaryPdf() {
        BigDecimal ten = BigDecimal.TEN;
        when(service.taxSummary(null, FROM, TO)).thenReturn(new TaxSummary(FROM, TO, ten, BigDecimal.ONE, ten, ten,
                BigDecimal.ZERO, ten, BigDecimal.ZERO, 2, 1));
        ResponseEntity<byte[]> r = controller.taxSummaryPdf(null, FROM, TO);
        assertPdf(r);
        assertThat(r.getHeaders().getContentDisposition().getFilename()).isEqualTo("tax-summary-2026-11-01-2026-11-30.pdf");
        verify(service).taxSummary(null, FROM, TO);
    }

    @Test
    void balancePdfsPassFiltersThrough() {
        List<BalanceRow> rows = List.of(new BalanceRow("5", "CUST-0001", "Khách hàng Thử", new BigDecimal("1500000"),
                new BigDecimal("100000"), new BigDecimal("1400000")));
        when(service.balances(EParty.CUSTOMER, 7L, true)).thenReturn(rows);
        when(service.balances(EParty.VENDOR, 7L, false)).thenReturn(List.of());
        assertPdf(controller.customerBalancesPdf(7L, true));
        assertPdf(controller.vendorBalancesPdf(7L, false));
        verify(service).balances(EParty.CUSTOMER, 7L, true);
        verify(service).balances(EParty.VENDOR, 7L, false);
    }

    @Test
    void tenantErrorsOfTheServicePropagate() {
        when(service.balances(EParty.CUSTOMER, null, false))
                .thenThrow(ApiException.badRequest(EAccountingError.ACC_001));
        assertThatThrownBy(() -> controller.customerBalancesPdf(null, false)).isInstanceOf(ApiException.class);
    }

    @Test
    void pdfEndpointsNeedTheReportPermission() throws Exception {
        for (var m : List.of(
                ReportController.class.getMethod("taxSummaryPdf", Long.class, LocalDate.class, LocalDate.class),
                ReportController.class.getMethod("customerBalancesPdf", Long.class, boolean.class),
                ReportController.class.getMethod("vendorBalancesPdf", Long.class, boolean.class))) {
            assertThat(m.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("@PermissionChecker.has('ACCOUNTING_REPORT_VIEW')");
        }
    }
}
