package com.terp.erp.commerce.pos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.common.EPosError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** POS reports (ERP manual 10): sales by day, month and warehouse; top products; top customers. */
@Service
@RequiredArgsConstructor
public class PosReportService {
    static final int TOP = 10;
    private static final long MAX_DAYS = 366;

    private final PosOrderRepository orders;
    private final PosService pos;

    public record DayRow(LocalDate date, long orders, BigDecimal total) {
    }

    public record MonthRow(String month, long orders, BigDecimal total) {
    }

    public record WarehouseRow(String warehouseId, String warehouseName, long orders, BigDecimal total) {
    }

    public record SalesReport(LocalDate from, LocalDate to, long orders, BigDecimal total, List<DayRow> daily,
                              List<MonthRow> monthly, List<WarehouseRow> byWarehouse) {
    }

    public record ProductRow(String itemId, String itemName, String sku, BigDecimal quantity, BigDecimal revenue,
                             long orders) {
    }

    /** top10 = highest revenue; all = every product sold, same order (the table and the quantity split). */
    public record ProductReport(LocalDate from, LocalDate to, BigDecimal totalQuantity, List<ProductRow> top10,
                                List<ProductRow> all) {
    }

    public record CustomerRow(String customerId, String customerName, long orders, BigDecimal spent,
                              BigDecimal averageOrderValue, LocalDate lastOrderDate) {
    }

    public record CustomerReport(LocalDate from, LocalDate to, List<CustomerRow> top10, List<CustomerRow> all) {
    }

    private record Period(Long companyId, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public SalesReport sales(Long companyId, LocalDate from, LocalDate to) {
        Period p = period(companyId, from, to);
        List<DayRow> daily = new ArrayList<>();
        Map<YearMonth, MonthRow> monthly = new LinkedHashMap<>();
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] r : orders.daily(p.companyId(), p.from(), p.to())) {
            LocalDate d = (LocalDate) r[0];
            long n = ((Number) r[1]).longValue();
            BigDecimal t = (BigDecimal) r[2];
            daily.add(new DayRow(d, n, t));
            monthly.merge(YearMonth.from(d), new MonthRow(YearMonth.from(d).toString(), n, t),
                    (a, b) -> new MonthRow(a.month(), a.orders() + b.orders(), a.total().add(b.total())));
            count += n;
            total = total.add(t);
        }
        List<WarehouseRow> byWarehouse = orders.byWarehouse(p.companyId(), p.from(), p.to()).stream()
                .map(r -> new WarehouseRow(r[0].toString(), (String) r[1], ((Number) r[2]).longValue(),
                        (BigDecimal) r[3]))
                .toList();
        return new SalesReport(p.from(), p.to(), count, total, daily, List.copyOf(monthly.values()), byWarehouse);
    }

    @Transactional(readOnly = true)
    public ProductReport products(Long companyId, LocalDate from, LocalDate to) {
        Period p = period(companyId, from, to);
        List<ProductRow> all = orders.byItem(p.companyId(), p.from(), p.to()).stream()
                .map(r -> new ProductRow(r[0].toString(), (String) r[1], (String) r[2], (BigDecimal) r[3],
                        (BigDecimal) r[4], ((Number) r[5]).longValue()))
                .toList();
        BigDecimal qty = all.stream().map(ProductRow::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProductReport(p.from(), p.to(), qty, all.stream().limit(TOP).toList(), all);
    }

    @Transactional(readOnly = true)
    public CustomerReport customers(Long companyId, LocalDate from, LocalDate to) {
        Period p = period(companyId, from, to);
        List<CustomerRow> all = orders.byCustomer(p.companyId(), p.from(), p.to()).stream().map(r -> {
            long n = ((Number) r[2]).longValue();
            BigDecimal spent = (BigDecimal) r[3];
            return new CustomerRow(r[0] == null ? null : r[0].toString(), (String) r[1], n, spent,
                    spent.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP), (LocalDate) r[4]);
        }).toList();
        return new CustomerReport(p.from(), p.to(), all.stream().limit(TOP).toList(), all);
    }

    /** Default: the last 30 days up to today. */
    private Period period(Long companyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(companyId, EPosError.POS_002);
        LocalDate t = to == null ? pos.today() : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t) || ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) {
            throw ApiException.badRequest(EPosError.POS_007);
        }
        return new Period(company, f, t);
    }
}
