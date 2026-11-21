package com.terp.erp.attendance.regularization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.record.AttendanceRecord;
import com.terp.erp.attendance.record.AttendanceRecordService;
import com.terp.erp.attendance.regularization.RegularizationDtos.RegularizationForm;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegularizationServiceTest {
    private final RegularizationRepository requests = mock(RegularizationRepository.class);
    private final AttendanceRecordService records = mock(AttendanceRecordService.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final RegularizationService service = new RegularizationService(requests, records, employees,
            Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createCopiesTheOriginalTimesOfTheRecord() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(records.require(40L)).thenReturn(record(t("09:30"), t("17:00")));
        when(requests.save(any())).thenAnswer(i -> {
            Regularization g = i.getArgument(0);
            g.setId(1L);
            return g;
        });
        var d = service.create(new RegularizationForm(7L, 40L, t("08:00"), null, " Device down "));
        assertThat(d.originalClockIn()).isEqualTo(t("09:30"));
        assertThat(d.requestedClockIn()).isEqualTo(t("08:00"));
        assertThat(d.status()).isEqualTo(ERegularizationStatus.PENDING);
        assertThat(d.reason()).isEqualTo("Device down");
    }

    @Test
    void formRules() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        assertError(() -> service.create(new RegularizationForm(7L, 40L, null, null, "x")), EAttendanceError.ATT_022);
        when(records.require(40L)).thenReturn(record(t("09:30"), t("17:00")));
        assertError(() -> service.create(new RegularizationForm(8L, 40L, t("08:00"), null, "x")), EAttendanceError.ATT_021);
        when(requests.existsByAttendanceRecordIdAndStatusAndIdNot(eq(40L), eq(ERegularizationStatus.PENDING), anyLong()))
                .thenReturn(true);
        assertError(() -> service.create(new RegularizationForm(7L, 40L, t("08:00"), null, "x")), EAttendanceError.ATT_019);
    }

    @Test
    void approveWritesRequestedTimesKeepingTheOmittedOne() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        Regularization g = request(ERegularizationStatus.PENDING);
        g.setRequestedClockIn(t("08:00"));
        when(requests.findById(1L)).thenReturn(Optional.of(g));
        AttendanceRecord r = record(t("09:30"), t("17:00"));
        when(records.require(40L)).thenReturn(r);
        var d = service.approve(1L, " ok ");
        verify(records).applyTimes(r, t("08:00"), t("17:00"));
        assertThat(d.status()).isEqualTo(ERegularizationStatus.APPROVED);
        assertThat(d.managerComments()).isEqualTo("ok");
        assertThat(d.decidedAt()).isNotNull();
    }

    @Test
    void decidedRequestCannotBeDecidedOrEditedAgain() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(requests.findById(1L)).thenReturn(Optional.of(request(ERegularizationStatus.REJECTED)));
        assertError(() -> service.approve(1L, null), EAttendanceError.ATT_020);
        assertError(() -> service.update(1L, new RegularizationForm(7L, 40L, t("08:00"), null, "x")),
                EAttendanceError.ATT_020);
        verify(records, never()).applyTimes(any(), any(), any());
    }

    @Test
    void rejectLeavesTheRecordUnchanged() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(requests.findById(1L)).thenReturn(Optional.of(request(ERegularizationStatus.PENDING)));
        assertThat(service.reject(1L, null).status()).isEqualTo(ERegularizationStatus.REJECTED);
        verify(records, never()).applyTimes(any(), any(), any());
    }

    @Test
    void otherCompanyRequestIsNotFound() {
        TestLogin.as(11L, "ATTENDANCE_VIEW");
        when(requests.findById(1L)).thenReturn(Optional.of(request(ERegularizationStatus.PENDING)));
        assertError(() -> service.get(1L), EAttendanceError.ATT_018);
    }

    @Test
    void approvalRateAndNameMatching() {
        assertThat(RegularizationService.rate(3, 1)).isEqualByComparingTo("75.0");
        assertThat(RegularizationService.rate(0, 0)).isEqualByComparingTo("0");
        Employee e = new Employee("7", null, "EMP000007", "A1", "Test Person", "t@example.invalid", null, null, null,
                "ACTIVE", null, null);
        assertThat(RegularizationService.matching(Map.of(7L, e), "person")).containsExactly(7L);
        assertThat(RegularizationService.matching(Map.of(7L, e), "nobody")).isEqualTo(List.of(-1L));
    }

    private static AttendanceRecord record(LocalTime in, LocalTime out) {
        AttendanceRecord r = new AttendanceRecord();
        r.setId(40L);
        r.setCompanyId(10L);
        r.setEmployeeId(7L);
        r.setWorkDate(LocalDate.of(2026, 11, 18));
        r.setClockIn(in);
        r.setClockOut(out);
        return r;
    }

    private static Regularization request(ERegularizationStatus status) {
        Regularization g = new Regularization();
        g.setId(1L);
        g.setCompanyId(10L);
        g.setEmployeeId(7L);
        g.setAttendanceRecordId(40L);
        g.setWorkDate(LocalDate.of(2026, 11, 18));
        g.setReason("x");
        g.setStatus(status);
        return g;
    }

    private static LocalTime t(String s) {
        return LocalTime.parse(s);
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
