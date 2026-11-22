package com.terp.erp.attendance.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.policy.AttendancePolicyRepository;
import com.terp.erp.attendance.record.ClockService.ClockForm;
import com.terp.erp.attendance.setting.AttendanceSetting;
import com.terp.erp.attendance.setting.AttendanceSettingRepository;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.attendance.setting.AttendanceSettingService.SettingsForm;
import com.terp.erp.attendance.setting.IpRules;
import com.terp.erp.attendance.shift.Shift;
import com.terp.erp.attendance.shift.ShiftRepository;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClockServiceTest {
    /** 2026-11-20 01:20 UTC = 08:20 in Vietnam, a Friday. */
    private static final Instant NOW = Instant.parse("2026-11-20T01:20:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private final AttendanceSettingRepository settingRepo = mock(AttendanceSettingRepository.class);
    private final AttendanceSettingService settings = new AttendanceSettingService(settingRepo);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ClockService service = new ClockService(records, shifts, policies, settings, employees, clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        TestLogin.as(1L, "ATTENDANCE_SELF");
        when(employees.me()).thenReturn(new Employee("12", "1", "E1", "EMP-1", "Fake Staff", null, null, null, null,
                "ACTIVE", "7", null));
        Shift s = new Shift();
        s.setId(7L);
        s.setCompanyId(1L);
        s.setName("Day");
        s.setStartTime(LocalTime.of(8, 0));
        s.setEndTime(LocalTime.of(17, 0));
        s.setBreakDurationMinutes(60);
        s.setGracePeriodMinutes(10);
        when(shifts.findById(7L)).thenReturn(Optional.of(s));
        when(records.save(any())).thenAnswer(i -> {
            AttendanceRecord r = i.getArgument(0);
            if (r.getId() == null) {
                r.setId(99L);
            }
            return r;
        });
    }

    private void restrict(String... ips) {
        AttendanceSetting st = new AttendanceSetting();
        st.setCompanyId(1L);
        st.setOffDays(EnumSet.of(DayOfWeek.SUNDAY));
        st.setIpRestrictionEnabled(true);
        st.setAllowedIps(String.join(",", ips));
        when(settingRepo.findByCompanyId(1L)).thenReturn(Optional.of(st));
    }

    private static AttendanceRecord open(Long companyId) {
        AttendanceRecord r = new AttendanceRecord();
        r.setId(50L);
        r.setCompanyId(companyId);
        r.setEmployeeId(12L);
        r.setWorkDate(TODAY);
        r.setClockIn(LocalTime.of(8, 0));
        r.setShiftId(7L);
        return r;
    }

    @Test
    void clockInComputesLateFromTheShift() {
        login();
        when(records.findByEmployeeIdAndWorkDate(12L, TODAY)).thenReturn(Optional.empty());
        var dto = service.clockIn(new ClockForm("on site"), "203.0.113.7");
        assertThat(dto.clockIn()).isEqualTo(LocalTime.of(8, 20));
        assertThat(dto.late()).isTrue();
        assertThat(dto.shiftId()).isEqualTo("7");
        assertThat(dto.notes()).isEqualTo("on site");
    }

    @Test
    void secondClockInTheSameDayIsRefused() {
        login();
        when(records.findByEmployeeIdAndWorkDate(12L, TODAY)).thenReturn(Optional.of(open(1L)));
        assertThatThrownBy(() -> service.clockIn(null, "203.0.113.7"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EAttendanceError.ATT_055);
        verify(records, never()).save(any());
    }

    @Test
    void clockOutWithoutClockInIsRefused() {
        login();
        when(records.findByEmployeeIdAndWorkDate(eq(12L), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.clockOut(null, "203.0.113.7"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EAttendanceError.ATT_056);
    }

    @Test
    void clockOutClosesTheOpenRecordWithEarlyDeparture() {
        login();
        when(records.findByEmployeeIdAndWorkDate(12L, TODAY)).thenReturn(Optional.of(open(1L)));
        var dto = service.clockOut(null, null);
        assertThat(dto.clockOut()).isEqualTo(LocalTime.of(8, 20));
        assertThat(dto.earlyDeparture()).isTrue();
        assertThat(dto.autoClockedOut()).isFalse();
    }

    @Test
    void disallowedIpIsForbiddenAndCidrIsAllowed() {
        login();
        restrict("198.51.100.4", "10.0.0.0/24");
        when(records.findByEmployeeIdAndWorkDate(12L, TODAY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.clockIn(null, "10.0.1.5"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EAttendanceError.ATT_054);
        assertThatThrownBy(() -> service.clockIn(null, null))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EAttendanceError.ATT_054);
        assertThat(service.clockIn(null, "10.0.0.200").clockIn()).isEqualTo(LocalTime.of(8, 20));
    }

    @Test
    void restrictionOffAcceptsAnyIp() {
        login();
        AttendanceSetting st = new AttendanceSetting();
        st.setCompanyId(1L);
        st.setOffDays(EnumSet.of(DayOfWeek.SUNDAY));
        st.setAllowedIps("198.51.100.4");
        when(settingRepo.findByCompanyId(1L)).thenReturn(Optional.of(st));
        when(records.findByEmployeeIdAndWorkDate(12L, TODAY)).thenReturn(Optional.empty());
        assertThat(service.clockIn(null, "192.0.2.99")).isNotNull();
    }

    @Test
    void anotherCompanysRecordIsNotFound() {
        login();
        when(records.findByEmployeeIdAndWorkDate(12L, TODAY)).thenReturn(Optional.of(open(2L)));
        assertThatThrownBy(() -> service.clockOut(null, "203.0.113.7"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EAttendanceError.ATT_008);
        verify(records, never()).save(any());
    }

    @Test
    void profileOfAnotherCompanyIsRefused() {
        TestLogin.as(1L, "ATTENDANCE_SELF");
        when(employees.me()).thenReturn(new Employee("12", "2", "E1", null, "Fake", null, null, null, null,
                "ACTIVE", null, null));
        assertThatThrownBy(() -> service.clockIn(null, "203.0.113.7"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(EAttendanceError.ATT_060);
    }

    @Test
    void settingsValidateIpsAndCidr() {
        TestLogin.as(1L, "ATTENDANCE_MANAGE");
        when(settingRepo.findByCompanyId(1L)).thenReturn(Optional.empty());
        when(settingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = settings.save(null, new SettingsForm(Set.of(DayOfWeek.SUNDAY), true,
                List.of(" 10.0.0.0/8 ", "2001:db8::/32", "203.0.113.7")));
        assertThat(saved.allowedIps()).containsExactly("10.0.0.0/8", "2001:db8::/32", "203.0.113.7");
        assertThatThrownBy(() -> settings.save(null, new SettingsForm(Set.of(DayOfWeek.SUNDAY), false,
                List.of("10.0.0.0/33")))).extracting("errorCode").isEqualTo(EAttendanceError.ATT_057);
        assertThatThrownBy(() -> settings.save(null, new SettingsForm(Set.of(DayOfWeek.SUNDAY), false,
                List.of("intranet.example.com")))).extracting("errorCode").isEqualTo(EAttendanceError.ATT_057);
        assertThatThrownBy(() -> settings.save(null, new SettingsForm(Set.of(DayOfWeek.SUNDAY), true, List.of())))
                .extracting("errorCode").isEqualTo(EAttendanceError.ATT_059);
        assertThat(IpRules.allowed("2001:db8:1::5", List.of("2001:db8::/32"))).isTrue();
        assertThat(IpRules.allowed("10.1.2.3", List.of("2001:db8::/32"))).isFalse();
    }

    // ------------------------------------------------------------------ auto clock-out

    private AutoClockOutService autoJob(Instant at) {
        AutoClockOutService job = new AutoClockOutService(records, shifts, policies, Clock.fixed(at, ZoneOffset.UTC));
        job.setAfterEndMinutes(60);
        return job;
    }

    @Test
    void autoClockOutClosesOnlyPastShiftRecordsAtTheShiftEndAndIsIdempotent() {
        login();
        AttendanceRecord past = open(1L);
        AttendanceRecord stillOnShift = open(1L);
        stillOnShift.setId(51L);
        stillOnShift.setWorkDate(TODAY.plusDays(1));
        when(records.findByClockInIsNotNullAndClockOutIsNullAndShiftIdIsNotNullAndWorkDateBetween(any(), any()))
                .thenReturn(List.of(past, stillOnShift));
        when(records.autoClockOut(eq(50L), any(), any(), any(), any(), any(), any(Boolean.class), any(), any()))
                .thenReturn(1, 0);
        // 2026-11-21 10:30 UTC = 17:30 Vietnam on the 21st: the 20th's 17:00 end + 60 min has passed, the 21st's not.
        AutoClockOutService job = autoJob(Instant.parse("2026-11-21T10:30:00Z"));
        assertThat(job.run()).isEqualTo(1);
        verify(records).autoClockOut(eq(50L), eq(LocalTime.of(17, 0)),
                eq(new BigDecimal("8.00")), eq(new BigDecimal("1.00")), eq(new BigDecimal("0.00")), any(),
                eq(false), eq(ERecordStatus.PRESENT), any());
        verify(records, never()).autoClockOut(eq(51L), any(), any(), any(), any(), any(), any(Boolean.class), any(),
                any());
        // Second run (another instance, or already closed): the conditional update touches nothing.
        assertThat(job.run()).isZero();
        verify(records, times(2)).autoClockOut(eq(50L), any(), any(), any(), any(), any(), any(Boolean.class), any(),
                any());
        assertThat(past.getClockOut()).isNull();
    }

    @Test
    void autoClockOutWaitsForTheEndPlusTheDelay() {
        login();
        when(records.findByClockInIsNotNullAndClockOutIsNullAndShiftIdIsNotNullAndWorkDateBetween(any(), any()))
                .thenReturn(List.of(open(1L)));
        // 17:30 Vietnam on the 20th: past the 17:00 end but within 60 minutes.
        assertThat(autoJob(Instant.parse("2026-11-20T10:30:00Z")).run()).isZero();
        verify(records, never()).autoClockOut(anyLong(), any(), any(), any(), any(), any(), any(Boolean.class), any(),
                any());
    }

    @Test
    void calendarCellListsLateEarlyOvertimeFlags() {
        var c = new RecordDtos.DayCell(TODAY, EDayStatus.PRESENT, false, true, true, true, null, null, null, "1");
        assertThat(c.flags()).containsExactly(EDayFlag.LATE, EDayFlag.EARLY, EDayFlag.OVERTIME);
        var none = new RecordDtos.DayCell(TODAY, EDayStatus.ABSENT, false, false, false, false, null, null, null, "1");
        assertThat(none.flags()).isEmpty();
    }
}
