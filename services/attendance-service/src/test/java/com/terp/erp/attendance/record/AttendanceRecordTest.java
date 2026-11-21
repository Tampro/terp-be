package com.terp.erp.attendance.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.client.EmployeeClient.Ref;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.policy.AttendancePolicy;
import com.terp.erp.attendance.policy.AttendancePolicyRepository;
import com.terp.erp.attendance.record.RecordDtos.RecordForm;
import com.terp.erp.attendance.setting.AttendanceSetting;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.attendance.shift.Shift;
import com.terp.erp.attendance.shift.ShiftRepository;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceRecordTest {
    /** 2026-11-19 20:00 UTC = 2026-11-20 in Vietnam (a Friday). */
    private static final Instant NOW = Instant.parse("2026-11-19T20:00:00Z");

    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private final AttendanceSettingService settings = mock(AttendanceSettingService.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final AttendanceRecordService service = new AttendanceRecordService(records, shifts, policies, settings,
            employees, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ calculator

    @Test
    void lateBeyondTheLargerGraceAndOvertimeAtThePolicyRate() {
        AttendanceRecord r = record(t("08:16"), t("19:16"));
        AttendanceCalculator.apply(r, shift(t("08:00"), t("17:00"), 60, 10), policy(15, 15));
        assertThat(r.isLate()).isTrue();
        assertThat(r.getTotalHours()).isEqualByComparingTo("10.00");
        assertThat(r.getOvertimeHours()).isEqualByComparingTo("2.00");
        assertThat(r.getOvertimeAmount()).isEqualByComparingTo("100000.00");
        assertThat(r.getStatus()).isEqualTo(ERecordStatus.PRESENT);
    }

    @Test
    void withinGraceIsNotLateAndEarlyDepartureUsesThePolicyGrace() {
        AttendanceRecord r = record(t("08:15"), t("16:40"));
        AttendanceCalculator.apply(r, shift(t("08:00"), t("17:00"), 60, 10), policy(15, 15));
        assertThat(r.isLate()).isFalse();
        assertThat(r.isEarlyDeparture()).isTrue();
    }

    @Test
    void nightShiftOverMidnight() {
        AttendanceRecord r = record(t("22:05"), t("06:00"));
        AttendanceCalculator.apply(r, shift(t("22:00"), t("06:00"), 30, 10), policy(15, 15));
        assertThat(r.isLate()).isFalse();
        assertThat(r.isEarlyDeparture()).isFalse();
        assertThat(r.getTotalHours()).isEqualByComparingTo("7.42");
    }

    @Test
    void halfDayBelowThresholdAbsentWithoutClockInHolidayBox() {
        AttendanceRecord half = record(t("08:00"), t("11:00"));
        AttendanceCalculator.apply(half, shift(t("08:00"), t("17:00"), 60, 0), policy(15, 15));
        assertThat(half.getStatus()).isEqualTo(ERecordStatus.HALF_DAY);

        AttendanceRecord absent = record(null, null);
        AttendanceCalculator.apply(absent, null, null);
        assertThat(absent.getStatus()).isEqualTo(ERecordStatus.ABSENT);
        assertThat(absent.isAbsent()).isTrue();

        AttendanceRecord holiday = record(null, null);
        holiday.setHoliday(true);
        AttendanceCalculator.apply(holiday, null, null);
        assertThat(holiday.getStatus()).isEqualTo(ERecordStatus.HOLIDAY);
    }

    @Test
    void stillAtWorkIsPresentWithZeroHours() {
        AttendanceRecord r = record(t("08:00"), null);
        AttendanceCalculator.apply(r, shift(t("08:00"), t("17:00"), 60, 0), null);
        assertThat(r.getStatus()).isEqualTo(ERecordStatus.PRESENT);
        assertThat(r.getTotalHours()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ calendar row

    @Test
    void calendarCellsAndTotals() {
        YearMonth ym = YearMonth.of(2026, 11);
        Employee e = employee("10");
        Holiday h = new Holiday("1", "Local day", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), false,
                List.of(new Ref("3", "HQ")));
        AttendanceRecord present = record(t("08:00"), t("17:00"));
        present.setId(1L);
        present.setWorkDate(LocalDate.of(2026, 11, 3));
        AttendanceRecord half = record(t("08:00"), t("11:00"));
        half.setId(2L);
        half.setStatus(ERecordStatus.HALF_DAY);
        var row = AttendanceRecordService.row(e, ym, AttendanceSetting.DEFAULT_OFF_DAYS, List.of(h),
                Map.of(LocalDate.of(2026, 11, 3), present, LocalDate.of(2026, 11, 4), half), LocalDate.of(2026, 11, 20));
        assertThat(row.days().get(0).status()).isEqualTo(EDayStatus.DAY_OFF);   // Sunday 1st
        assertThat(row.days().get(1).status()).isEqualTo(EDayStatus.HOLIDAY);   // Monday 2nd, HQ holiday
        assertThat(row.days().get(2).status()).isEqualTo(EDayStatus.PRESENT);
        assertThat(row.days().get(4).status()).isEqualTo(EDayStatus.NOT_ADDED);
        assertThat(row.days().get(20).status()).isEqualTo(EDayStatus.DAY_OFF);  // Saturday 21st
        assertThat(row.days().get(22).status()).isEqualTo(EDayStatus.FUTURE);   // Monday 23rd
        assertThat(row.attended()).isEqualByComparingTo("1.5");
        assertThat(row.workingDays()).isEqualTo(20);                           // 21 weekdays minus the holiday
    }

    // ------------------------------------------------------------------ service rules

    @Test
    void createComputesFromTheProfileShiftAndPolicy() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(employees.get(7L)).thenReturn(employee("10"));
        when(shifts.findById(1L)).thenReturn(Optional.of(shift(t("08:00"), t("17:00"), 60, 0)));
        when(policies.findById(2L)).thenReturn(Optional.of(policy(15, 15)));
        when(settings.offDays(10L)).thenReturn(EnumSet.copyOf(AttendanceSetting.DEFAULT_OFF_DAYS));
        when(records.save(any())).thenAnswer(i -> {
            AttendanceRecord r = i.getArgument(0);
            r.setId(5L);
            return r;
        });
        var d = service.create(new RecordForm(7L, LocalDate.of(2026, 11, 20), t("08:30"), t("17:00"), false, " WFH "));
        assertThat(d.late()).isTrue();
        assertThat(d.shiftId()).isEqualTo("1");
        assertThat(d.notes()).isEqualTo("WFH");
        assertThat(d.weekend()).isFalse();
    }

    @Test
    void recordRules() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(employees.get(7L)).thenReturn(employee("10"));
        when(settings.offDays(10L)).thenReturn(EnumSet.copyOf(AttendanceSetting.DEFAULT_OFF_DAYS));
        assertError(() -> service.create(new RecordForm(7L, LocalDate.of(2026, 11, 20), null, t("17:00"), false, null)),
                EAttendanceError.ATT_010);
        assertError(() -> service.create(new RecordForm(7L, LocalDate.of(2026, 11, 21), t("08:00"), null, false, null)),
                EAttendanceError.ATT_011);
        when(records.existsByEmployeeIdAndWorkDateAndIdNot(eq(7L), eq(LocalDate.of(2026, 11, 20)), anyLong()))
                .thenReturn(true);
        assertError(() -> service.create(new RecordForm(7L, LocalDate.of(2026, 11, 20), t("08:00"), null, false, null)),
                EAttendanceError.ATT_009);
    }

    @Test
    void anotherCompanysEmployeeOrRecordIsNotFound() {
        TestLogin.as(11L, "ATTENDANCE_MANAGE");
        when(employees.get(7L)).thenReturn(employee("10"));
        assertError(() -> service.create(new RecordForm(7L, LocalDate.of(2026, 11, 20), null, null, false, null)),
                EAttendanceError.ATT_008);
        AttendanceRecord r = record(null, null);
        r.setId(5L);
        when(records.findById(5L)).thenReturn(Optional.of(r));
        assertError(() -> service.get(5L), EAttendanceError.ATT_008);
    }

    @Test
    void exportCellCodes() {
        var c = new RecordDtos.DayCell(LocalDate.of(2026, 11, 3), EDayStatus.PRESENT, false, true, false, true,
                null, null, null, "1");
        assertThat(AttendanceExcelService.code(c)).isEqualTo("P+L+OT");
    }

    private static AttendanceRecord record(LocalTime in, LocalTime out) {
        AttendanceRecord r = new AttendanceRecord();
        r.setCompanyId(10L);
        r.setEmployeeId(7L);
        r.setWorkDate(LocalDate.of(2026, 11, 18));
        r.setClockIn(in);
        r.setClockOut(out);
        return r;
    }

    private static Shift shift(LocalTime start, LocalTime end, int breakMin, int grace) {
        Shift s = new Shift();
        s.setId(1L);
        s.setCompanyId(10L);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setBreakDurationMinutes(breakMin);
        s.setGracePeriodMinutes(grace);
        return s;
    }

    private static AttendancePolicy policy(int late, int early) {
        AttendancePolicy p = new AttendancePolicy();
        p.setId(2L);
        p.setCompanyId(10L);
        p.setLateArrivalGraceMinutes(late);
        p.setEarlyDepartureGraceMinutes(early);
        p.setOvertimeRatePerHour(new BigDecimal("50000"));
        return p;
    }

    private static Employee employee(String companyId) {
        return new Employee("7", companyId, "EMP000007", null, "Test Person", "t@example.invalid", null,
                new Ref("3", "HQ"), new Ref("4", "Developer"), "ACTIVE", "1", "2");
    }

    private static LocalTime t(String s) {
        return LocalTime.parse(s);
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
