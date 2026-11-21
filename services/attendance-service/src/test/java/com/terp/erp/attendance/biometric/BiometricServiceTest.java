package com.terp.erp.attendance.biometric;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceForm;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchBatch;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchIn;
import com.terp.erp.attendance.biometric.BiometricDtos.SyncForm;
import com.terp.erp.attendance.biometric.DeviceKeyFilter.DevicePrincipal;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.config.DeviceRateLimitProperties;
import com.terp.erp.attendance.record.AttendanceRecord;
import com.terp.erp.attendance.record.AttendanceRecordRepository;
import com.terp.erp.attendance.record.AttendanceRecordService;
import com.terp.erp.attendance.record.RecordDtos.RecordForm;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BiometricServiceTest {
    private final BiometricDeviceRepository devices = mock(BiometricDeviceRepository.class);
    private final BiometricPunchRepository punches = mock(BiometricPunchRepository.class);
    private final AttendanceRecordService recordService = mock(AttendanceRecordService.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final BiometricService service = new BiometricService(devices, punches, recordService, records, employees,
            Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void keysAreRandomAndOnlyTheHashIsStored() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(devices.save(any())).thenAnswer(i -> {
            BiometricDevice d = i.getArgument(0);
            d.setId(3L);
            return d;
        });
        var created = service.createDevice(null, new DeviceForm("Front door", "SN-1", null, null));
        String key = created.apiKey();
        assertThat(key).startsWith("bk_").hasSizeGreaterThan(40);
        assertThat(created.device().keyPrefix()).isEqualTo(key.substring(0, 8));
        assertThat(DeviceKeys.hash(key)).hasSize(64).isNotEqualTo(key);
        assertThat(DeviceKeys.generate()).isNotEqualTo(DeviceKeys.generate());
    }

    @Test
    void serialTakenAndOtherCompanyDevice() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(devices.serialTaken(10L, "SN-1", -1L)).thenReturn(true);
        assertError(() -> service.createDevice(null, new DeviceForm("X", "SN-1", null, null)), EAttendanceError.ATT_028);
        BiometricDevice d = device(11L, EStatus.ACTIVE);
        when(devices.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(d));
        assertError(() -> service.rotateKey(3L), EAttendanceError.ATT_023);
    }

    @Test
    void filterAcceptsAnActiveDeviceKeyAndRefusesTheRest() throws Exception {
        String key = DeviceKeys.generate();
        BiometricDevice active = device(10L, EStatus.ACTIVE);
        active.setApiKeyHash(DeviceKeys.hash(key));
        when(devices.findByApiKeyHashAndDeletedFalse(DeviceKeys.hash(key))).thenReturn(Optional.of(active));
        DeviceKeyFilter filter = new DeviceKeyFilter(devices, new ObjectMapper(),
                new DeviceRateLimitProperties(100, 100, 100, false), Clock.systemUTC());

        MockHttpServletRequest ok = new MockHttpServletRequest("POST", "/api/v1/attendance/biometric/punches");
        ok.addHeader(DeviceKeyFilter.HEADER, key);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(ok, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        MockHttpServletRequest bad = new MockHttpServletRequest("POST", "/api/v1/attendance/biometric/punches");
        bad.addHeader(DeviceKeyFilter.HEADER, "bk_wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain stopped = new MockFilterChain();
        filter.doFilter(bad, res, stopped);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("ATT_024");
        assertThat(stopped.getRequest()).isNull();

        active.setStatus(EStatus.INACTIVE);
        MockHttpServletResponse off = new MockHttpServletResponse();
        filter.doFilter(ok, off, new MockFilterChain());
        assertThat(off.getStatus()).isEqualTo(401);
    }

    @Test
    void tooManyRequestsOfADeviceAndTooManyWrongKeysFromAnIpGet429() throws Exception {
        String key = DeviceKeys.generate();
        BiometricDevice active = device(10L, EStatus.ACTIVE);
        active.setApiKeyHash(DeviceKeys.hash(key));
        when(devices.findByApiKeyHashAndDeletedFalse(DeviceKeys.hash(key))).thenReturn(Optional.of(active));
        DeviceKeyFilter filter = new DeviceKeyFilter(devices, new ObjectMapper(),
                new DeviceRateLimitProperties(60, 2, 2, true), Clock.systemUTC());
        int[] statuses = new int[3];
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/attendance/biometric/punches");
            req.addHeader(DeviceKeyFilter.HEADER, key);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            statuses[i] = res.getStatus();
        }
        assertThat(statuses).containsExactly(200, 200, 429);

        int[] wrong = new int[4];
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/attendance/biometric/punches");
            req.addHeader(DeviceKeyFilter.HEADER, "bk_guess" + i);
            req.addHeader("X-Forwarded-For", "6.6.6.6, 10.0.0.5");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            wrong[i] = res.getStatus();
            if (i == 2) {
                assertThat(res.getHeader("Retry-After")).isNotBlank();
                assertThat(res.getContentAsString()).contains("COMMON_006");
            }
        }
        assertThat(wrong).containsExactly(401, 401, 429, 429);

        // Another client IP behind the same gateway is not affected.
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/v1/attendance/biometric/punches");
        other.addHeader(DeviceKeyFilter.HEADER, "bk_typo");
        other.addHeader("X-Forwarded-For", "10.0.0.6");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(other, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(filter.clientIp(other)).isEqualTo("10.0.0.6");
    }

    @Test
    void receiveSkipsKnownAndRepeatedExternalIds() {
        when(punches.existingExternalIds(eq(3L), anyCollection())).thenReturn(List.of("a"));
        var res = service.receive(new DevicePrincipal(3L, 10L), new PunchBatch(List.of(
                new PunchIn("EMP1", LocalDateTime.parse("2026-11-18T08:00:00"), "a"),
                new PunchIn("EMP1", LocalDateTime.parse("2026-11-18T12:00:00"), "b"),
                new PunchIn("EMP1", LocalDateTime.parse("2026-11-18T12:00:00"), "b"),
                new PunchIn("EMP2", LocalDateTime.parse("2026-11-18T17:00:00.5"), null))));
        assertThat(res.received()).isEqualTo(4);
        assertThat(res.stored()).isEqualTo(2);
        assertThat(res.duplicates()).isEqualTo(2);
        assertError(() -> service.receive(new DevicePrincipal(3L, 10L), new PunchBatch(List.of())),
                EAttendanceError.ATT_025);
    }

    @Test
    void syncFirstInLastOutOverwritesTheRecord() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        LocalDate day = LocalDate.of(2026, 11, 18);
        when(punches.ofCode(eq(10L), eq("a1"), any(), any())).thenReturn(List.of(
                punch("A1", "08:01"), punch("A1", "12:00"), punch("A1", "17:05")));
        when(employees.all(null)).thenReturn(List.of(employee()));
        AttendanceRecord existing = new AttendanceRecord();
        existing.setId(40L);
        existing.setCompanyId(10L);
        existing.setNotes("hand entry");
        when(records.findByEmployeeIdAndWorkDate(7L, day)).thenReturn(Optional.of(existing));
        when(recordService.save(any(), anyLong(), any(), any())).thenAnswer(i -> i.getArgument(0));
        var res = service.sync(null, new SyncForm(day, "a1"));
        verify(recordService).save(existing, 10L, employee(),
                new RecordForm(7L, day, LocalTime.parse("08:01"), LocalTime.parse("17:05"), false, "hand entry"));
        assertThat(res.created()).isFalse();
        assertThat(res.punches()).isEqualTo(3);
        assertThat(existing.getBiometricId()).isEqualTo("A1");
    }

    @Test
    void syncErrors() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        LocalDate day = LocalDate.of(2026, 11, 18);
        when(punches.ofCode(eq(10L), eq("NONE"), any(), any())).thenReturn(List.of());
        assertError(() -> service.sync(null, new SyncForm(day, "NONE")), EAttendanceError.ATT_026);
        when(punches.ofCode(eq(10L), eq("ZZ"), any(), any())).thenReturn(List.of(punch("ZZ", "08:00")));
        when(employees.all(null)).thenReturn(List.of(employee()));
        assertError(() -> service.sync(null, new SyncForm(day, "ZZ")), EAttendanceError.ATT_027);
    }

    @Test
    void dayRowWithOnePunchHasNoLastOut() {
        var row = BiometricService.row("A1", employee(), List.of(punch("A1", "08:01")));
        assertThat(row.firstIn()).isEqualTo(LocalTime.parse("08:01"));
        assertThat(row.lastOut()).isNull();
        assertThat(row.synced()).isFalse();
        assertThat(BiometricService.byCode(List.of(employee()))).containsKey("a1");
    }

    private static BiometricDevice device(Long companyId, EStatus status) {
        BiometricDevice d = new BiometricDevice();
        d.setId(3L);
        d.setCompanyId(companyId);
        d.setStatus(status);
        return d;
    }

    private static BiometricPunch punch(String code, String time) {
        BiometricPunch p = new BiometricPunch();
        p.setId((long) time.hashCode());
        p.setCompanyId(10L);
        p.setDeviceId(3L);
        p.setEmployeeCode(code);
        p.setPunchedAt(LocalDateTime.of(LocalDate.of(2026, 11, 18), LocalTime.parse(time)));
        return p;
    }

    private static Employee employee() {
        return new Employee("7", null, "EMP000007", "A1", "Test Person", "t@example.invalid", null, null, null,
                "ACTIVE", null, null);
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
