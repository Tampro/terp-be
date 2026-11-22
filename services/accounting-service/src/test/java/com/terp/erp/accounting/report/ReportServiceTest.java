package com.terp.erp.accounting.report;

import org.junit.jupiter.api.Test;
import com.terp.erp.accounting.client.CommerceClient.Invoice;
import com.terp.erp.accounting.report.ReportService.AgingReport;
import com.terp.erp.accounting.report.ReportService.AgingRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 30);

    private static Invoice inv(long party, String name, LocalDate due, String balance) {
        return new Invoice(1L, "SI", party, name, due.minusDays(30), due, BigDecimal.ZERO, new BigDecimal(balance),
                new BigDecimal(balance), "POSTED");
    }

    @Test
    void agingPutsEachBalanceInItsBucketPerPartyWithTotals() {
        AgingReport r = ReportService.aging(ReportService.EParty.CUSTOMER, AS_OF, List.of(
                inv(5, "Buyer A", AS_OF, "100"),               // due today: current
                inv(5, "Buyer A", AS_OF.minusDays(1), "10"),   // 1 day late
                inv(5, "Buyer A", AS_OF.minusDays(30), "20"),  // 30 days late
                inv(6, "Buyer B", AS_OF.minusDays(31), "30"),
                inv(6, "Buyer B", AS_OF.minusDays(90), "40"),
                inv(6, "Buyer B", AS_OF.minusDays(91), "50"),
                inv(6, "Buyer B", AS_OF.minusDays(200), "0"))); // nothing open: left out

        assertThat(r.rows()).hasSize(2);
        AgingRow a = r.rows().get(0);
        assertThat(a.partyName()).isEqualTo("Buyer A");
        assertThat(a.current()).isEqualByComparingTo("100");
        assertThat(a.days1To30()).isEqualByComparingTo("30");
        assertThat(a.total()).isEqualByComparingTo("130");
        AgingRow b = r.rows().get(1);
        assertThat(b.days31To60()).isEqualByComparingTo("30");
        assertThat(b.days61To90()).isEqualByComparingTo("40");
        assertThat(b.over90()).isEqualByComparingTo("50");
        assertThat(r.totals().total()).isEqualByComparingTo("250");
        assertThat(r.totals().over90()).isEqualByComparingTo("50");
    }

    @Test
    void agingPdfIsAPdf() {
        AgingReport r = ReportService.aging(ReportService.EParty.VENDOR, AS_OF,
                List.of(inv(3, "Nhà cung cấp Thử", AS_OF.minusDays(5), "1500000")));

        byte[] pdf = ReportPdf.aging(r);

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
