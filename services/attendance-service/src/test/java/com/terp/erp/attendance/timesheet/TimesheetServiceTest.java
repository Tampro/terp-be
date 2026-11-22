package com.terp.erp.attendance.timesheet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.attendance.timesheet.TimesheetDtos.EntryForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimesheetServiceTest {
    private static final LocalDate WED = LocalDate.of(2026, 11, 18);

    private final TimeEntryRepository entries = mock(TimeEntryRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final TimesheetService service = new TimesheetService(entries, employees,
            Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void weekRowSumsHoursAndDayStatus() {
        var row = TimesheetService.row(employee("10"), LocalDate.of(2026, 11, 16), List.of(
                entry(WED, "6.5", ERegularizationStatus.APPROVED), entry(WED, "1.5", ERegularizationStatus.PENDING),
                entry(WED.plusDays(1), "8", ERegularizationStatus.REJECTED)));
        assertThat(row.days()).hasSize(7);
        assertThat(row.days().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(row.days().get(2).hours()).isEqualByComparingTo("8");
        assertThat(row.days().get(2).entries()).isEqualTo(2);
        assertThat(row.days().get(2).status()).isEqualTo(ERegularizationStatus.PENDING);
        assertThat(row.days().get(3).status()).isEqualTo(ERegularizationStatus.REJECTED);
        assertThat(row.days().get(0).status()).isNull();
        assertThat(row.total()).isEqualByComparingTo("16");
    }

    @Test
    void minutesAddToHoursTypeDefaultsToManualAndTheTotalIsAtLeastAQuarter() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(employees.get(7L)).thenReturn(employee("10"));
        when(entries.hoursOfDay(eq(7L), eq(WED), anyLong())).thenReturn(BigDecimal.ZERO);
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
        TimeEntry t = new TimeEntry();
        service.save(t, 10L, new EntryForm(7L, WED, new BigDecimal("2"), null, "Review", null, 30));
        assertThat(t.getHours()).isEqualByComparingTo("2.50");
        assertThat(t.getEntryType()).isEqualTo(ETimeEntryType.MANUAL);
        service.save(t, 10L, new EntryForm(7L, WED, BigDecimal.ZERO, "ERP", "Review", ETimeEntryType.PROJECT, 20));
        assertThat(t.getHours()).isEqualByComparingTo("0.33");
        assertThat(t.getEntryType()).isEqualTo(ETimeEntryType.PROJECT);
        assertError(() -> service.save(new TimeEntry(), 10L, new EntryForm(7L, WED, BigDecimal.ZERO, null, "x", null, 10)),
                EAttendanceError.ATT_053);
    }

    @Test
    void monthCellsCoverEveryDayOfTheMonth() {
        var cells = TimesheetService.cells(LocalDate.of(2026, 2, 1), 28, List.of(
                entry(LocalDate.of(2026, 2, 3), "6.5", ERegularizationStatus.APPROVED),
                entry(LocalDate.of(2026, 2, 3), "1.5", ERegularizationStatus.APPROVED)));
        assertThat(cells).hasSize(28);
        assertThat(cells.get(2).hours()).isEqualByComparingTo("8");
        assertThat(cells.get(27).date()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void createRules() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(employees.get(7L)).thenReturn(employee("10"));
        when(entries.hoursOfDay(eq(7L), eq(WED), anyLong())).thenReturn(new BigDecimal("20"));
        assertError(() -> service.create(form(WED, "5")), EAttendanceError.ATT_031);
        assertError(() -> service.create(form(LocalDate.of(2026, 11, 20), "1")), EAttendanceError.ATT_032);
        when(entries.save(any())).thenAnswer(i -> {
            TimeEntry t = i.getArgument(0);
            t.setId(1L);
            return t;
        });
        var d = service.create(form(WED, "4"));
        assertThat(d.status()).isEqualTo(ERegularizationStatus.PENDING);
        assertThat(d.project()).isEqualTo("ERP");
    }

    @Test
    void approvedIsFinalAndRejectedGoesBackToPending() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(employees.get(7L)).thenReturn(employee("10"));
        when(entries.hoursOfDay(anyLong(), any(), anyLong())).thenReturn(BigDecimal.ZERO);
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
        TimeEntry approved = entry(WED, "4", ERegularizationStatus.APPROVED);
        when(entries.findById(1L)).thenReturn(Optional.of(approved));
        assertError(() -> service.update(1L, form(WED, "5")), EAttendanceError.ATT_030);
        assertError(() -> service.reject(1L, null), EAttendanceError.ATT_020);

        TimeEntry rejected = entry(WED, "4", ERegularizationStatus.REJECTED);
        rejected.setManagerComments("too much");
        when(entries.findById(2L)).thenReturn(Optional.of(rejected));
        var d = service.update(2L, form(WED, "3"));
        assertThat(d.status()).isEqualTo(ERegularizationStatus.PENDING);
        assertThat(d.managerComments()).isNull();
    }

    @Test
    void approveRecordsTheDecision() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(entries.findById(1L)).thenReturn(Optional.of(entry(WED, "4", ERegularizationStatus.PENDING)));
        var d = service.approve(1L, " ok ");
        assertThat(d.status()).isEqualTo(ERegularizationStatus.APPROVED);
        assertThat(d.managerComments()).isEqualTo("ok");
        assertThat(d.decidedAt()).isNotNull();
    }

    @Test
    void otherCompanyEntryOrEmployeeIsNotFound() {
        TestLogin.as(11L, "ATTENDANCE_MANAGE");
        when(entries.findById(1L)).thenReturn(Optional.of(entry(WED, "4", ERegularizationStatus.PENDING)));
        assertError(() -> service.get(1L), EAttendanceError.ATT_029);
        when(employees.get(7L)).thenReturn(employee("10"));
        assertError(() -> service.create(form(WED, "1")), EAttendanceError.ATT_029);
    }

    @Test
    void csvFieldsHandleQuotes() {
        assertThat(TimesheetFileService.csvFields("Test Person,2026-11-18,6.5,\"ERP, portal\",\"say \"\"hi\"\"\""))
                .containsExactly("Test Person", "2026-11-18", "6.5", "ERP, portal", "say \"hi\"");
    }

    private static EntryForm form(LocalDate date, String hours) {
        return new EntryForm(7L, date, new BigDecimal(hours), " ERP ", " Code review ", null, null);
    }

    private static TimeEntry entry(LocalDate date, String hours, ERegularizationStatus status) {
        TimeEntry t = new TimeEntry();
        t.setId(1L);
        t.setCompanyId(10L);
        t.setEmployeeId(7L);
        t.setWorkDate(date);
        t.setHours(new BigDecimal(hours));
        t.setDescription("x");
        t.setStatus(status);
        return t;
    }

    private static Employee employee(String companyId) {
        return new Employee("7", companyId, "EMP000007", null, "Test Person", "t@example.invalid", null, null, null,
                "ACTIVE", null, null);
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
