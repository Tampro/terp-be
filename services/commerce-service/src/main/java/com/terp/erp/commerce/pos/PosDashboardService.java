package com.terp.erp.commerce.pos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.common.EPosError;
import com.terp.erp.commerce.pos.PosReportService.ProductRow;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** POS dashboard (ERP manual 5.5): today's sales, year totals, monthly orders, hour x weekday heat map, top products. */
@Service
@RequiredArgsConstructor
public class PosDashboardService {
    /** TODO(assumption): report-service passes the company's IAM defaultTimezone; this is only the fallback. */
    static final String DEFAULT_TZ = "Asia/Ho_Chi_Minh";

    private final PosOrderRepository orders;
    private final Clock clock;

    public record MonthCell(int month, long orders, BigDecimal total) {
    }

    public record HeatCell(int dayOfWeek, int hour, long orders, BigDecimal total) {
    }

    public record PosDashboard(int year, LocalDate today, long todayOrders, BigDecimal todayRevenue, long totalOrders,
                               BigDecimal totalRevenue, BigDecimal averageTransaction, List<MonthCell> monthly,
                               List<HeatCell> heatmap, List<ProductRow> topProducts) {
    }

    @Transactional(readOnly = true)
    public PosDashboard dashboard(Long companyId, Integer year, String tz) {
        Long company = TenantGuard.resolveCompany(companyId, EPosError.POS_002);
        ZoneId zone = zone(tz);
        // orderDate is written as LocalDate.now(clock) at checkout and the clock is UTC (ClockConfig), so an order
        // placed just after midnight in tz still carries the previous UTC date; the heat map uses createdDate in tz.
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int y = year == null ? today.getYear() : year;
        LocalDate from = LocalDate.of(y, 1, 1);
        LocalDate to = LocalDate.of(y, 12, 31);

        long todayOrders = 0;
        BigDecimal todayRevenue = BigDecimal.ZERO;
        for (Object[] r : orders.daily(company, today, today)) {
            todayOrders += num(r[1]);
            todayRevenue = todayRevenue.add(money(r[2]));
        }

        long[] mOrders = new long[13];
        BigDecimal[] mTotal = new BigDecimal[13];
        Arrays.fill(mTotal, BigDecimal.ZERO);
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Object[] r : orders.daily(company, from, to)) {
            int m = ((LocalDate) r[0]).getMonthValue();
            mOrders[m] += num(r[1]);
            mTotal[m] = mTotal[m].add(money(r[2]));
            totalOrders += num(r[1]);
            totalRevenue = totalRevenue.add(money(r[2]));
        }
        List<MonthCell> monthly = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthly.add(new MonthCell(m, mOrders[m], mTotal[m]));
        }

        long[][] hOrders = new long[8][24];
        BigDecimal[][] hTotal = new BigDecimal[8][24];
        for (BigDecimal[] row : hTotal) {
            Arrays.fill(row, BigDecimal.ZERO);
        }
        for (Object[] r : orders.createdAndTotal(company, from, to)) {
            if (r[0] == null) {
                continue;
            }
            ZonedDateTime t = ((Instant) r[0]).atZone(zone);
            int d = t.getDayOfWeek().getValue();
            int h = t.getHour();
            hOrders[d][h]++;
            hTotal[d][h] = hTotal[d][h].add(money(r[1]));
        }
        List<HeatCell> heat = new ArrayList<>(168);
        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                heat.add(new HeatCell(d, h, hOrders[d][h], hTotal[d][h]));
            }
        }

        List<ProductRow> top = orders.byItem(company, from, to).stream().limit(PosReportService.TOP)
                .map(r -> new ProductRow(r[0].toString(), (String) r[1], (String) r[2], (BigDecimal) r[3],
                        (BigDecimal) r[4], num(r[5])))
                .toList();
        BigDecimal avg = totalOrders == 0 ? null
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        return new PosDashboard(y, today, todayOrders, todayRevenue, totalOrders, totalRevenue, avg, monthly, heat,
                top);
    }

    static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.of(DEFAULT_TZ);
        }
        try {
            return ZoneId.of(tz.trim());
        } catch (DateTimeException e) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static BigDecimal money(Object o) {
        return o == null ? BigDecimal.ZERO : (BigDecimal) o;
    }
}
