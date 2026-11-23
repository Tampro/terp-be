package com.terp.erp.commerce.pos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.commerce.TestLogin;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosDashboardServiceTest {
    /** 2026-11-22 18:30 UTC = 2026-11-23 01:30 in Asia/Ho_Chi_Minh (a Monday). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T18:30:00Z"), ZoneOffset.UTC);

    private final PosOrderRepository orders = mock(PosOrderRepository.class);
    private final PosDashboardService service = new PosDashboardService(orders, CLOCK);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void figuresOfTheCallersCompanyInItsZone() {
        TestLogin.as(10L, "POS_REPORT");
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(orders.daily(10L, today, today)).thenReturn(List.<Object[]>of(
                new Object[]{today, 2L, new BigDecimal("300")}));
        when(orders.daily(10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).thenReturn(List.of(
                new Object[]{LocalDate.of(2026, 3, 2), 1L, new BigDecimal("100")},
                new Object[]{today, 2L, new BigDecimal("300")}));
        when(orders.createdAndTotal(eq(10L), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{Instant.parse("2026-11-22T18:30:00Z"), new BigDecimal("300")}));
        when(orders.byItem(eq(10L), any(), any())).thenReturn(List.<Object[]>of(
                new Object[]{7L, "Tea", "T-1", new BigDecimal("3"), new BigDecimal("400"), 3L}));

        var d = service.dashboard(99L, null, null);

        assertThat(d.today()).isEqualTo(today);
        assertThat(d.year()).isEqualTo(2026);
        assertThat(d.todayOrders()).isEqualTo(2);
        assertThat(d.todayRevenue()).isEqualByComparingTo("300");
        assertThat(d.totalOrders()).isEqualTo(3);
        assertThat(d.totalRevenue()).isEqualByComparingTo("400");
        assertThat(d.averageTransaction()).isEqualByComparingTo("133.33");
        assertThat(d.monthly()).hasSize(12);
        assertThat(d.monthly().get(2).orders()).isEqualTo(1);
        assertThat(d.monthly().get(10).total()).isEqualByComparingTo("300");
        assertThat(d.heatmap()).hasSize(168);
        var cell = d.heatmap().stream().filter(c -> c.orders() > 0).toList();
        assertThat(cell).hasSize(1);
        assertThat(cell.get(0).dayOfWeek()).isEqualTo(1);
        assertThat(cell.get(0).hour()).isEqualTo(1);
        assertThat(d.topProducts()).hasSize(1);
        assertThat(d.topProducts().get(0).itemId()).isEqualTo("7");
        // A company user's requested companyId (99) is ignored: only its own company is read.
        verify(orders, never()).daily(eq(99L), any(), any());
    }

    @Test
    void noOrdersMeansNoAverage() {
        TestLogin.as(10L, "POS_REPORT");
        var d = service.dashboard(null, 2025, "UTC");
        assertThat(d.averageTransaction()).isNull();
        assertThat(d.totalOrders()).isZero();
        assertThat(d.heatmap()).allMatch(c -> c.orders() == 0);
    }

    @Test
    void unknownZoneIsRejected() {
        TestLogin.as(10L, "POS_REPORT");
        assertThatThrownBy(() -> service.dashboard(null, null, "Mars/Base"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void handlerNeedsPosReport() throws Exception {
        PreAuthorize p = PosController.class.getMethod("dashboard", Long.class, Integer.class, String.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(p.value()).isEqualTo("@PermissionChecker.has('POS_REPORT')");
    }
}
