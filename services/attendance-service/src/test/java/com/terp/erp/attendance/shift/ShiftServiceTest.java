package com.terp.erp.attendance.shift;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.shift.ShiftDtos.ShiftForm;
import com.terp.erp.exception.ApiException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShiftServiceTest {
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final ShiftService service = new ShiftService(shifts);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dayShiftWorkingTimeIsSpanMinusBreak() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(shifts.save(any())).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setId(1L);
            return s;
        });
        var d = service.create(null, form(t("08:00"), t("17:00"), 60, t("12:00"), t("13:00"), false));
        assertThat(d.workingMinutes()).isEqualTo(480);
        assertThat(d.status()).isEqualTo(EStatus.ACTIVE);
        assertThat(d.companyId()).isEqualTo("10");
    }

    @Test
    void nightShiftRunsOverMidnightWithABreakAfterMidnight() {
        ShiftService.validate(form(t("22:00"), t("06:00"), 30, t("02:00"), t("02:30"), true));
        Shift s = new Shift();
        s.setStartTime(t("22:00"));
        s.setEndTime(t("06:00"));
        s.setBreakDurationMinutes(30);
        assertThat(s.workingMinutes()).isEqualTo(450);
    }

    @Test
    void endBeforeStartNeedsNightShift() {
        assertError(() -> ShiftService.validate(form(t("22:00"), t("06:00"), 0, null, null, false)),
                EAttendanceError.ATT_005);
    }

    @Test
    void breakTimesBothOrNoneAndInsideTheShift() {
        assertError(() -> ShiftService.validate(form(t("08:00"), t("17:00"), 60, t("12:00"), null, false)),
                EAttendanceError.ATT_006);
        assertError(() -> ShiftService.validate(form(t("08:00"), t("17:00"), 60, t("18:00"), t("19:00"), false)),
                EAttendanceError.ATT_006);
        assertError(() -> ShiftService.validate(form(t("08:00"), t("17:00"), 60, t("13:00"), t("12:00"), false)),
                EAttendanceError.ATT_006);
    }

    @Test
    void breakAsLongAsTheShiftIsRefused() {
        assertError(() -> ShiftService.validate(form(t("08:00"), t("09:00"), 60, null, null, false)),
                EAttendanceError.ATT_007);
    }

    @Test
    void nameTakenInTheCompany() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(shifts.nameTaken(10L, "Morning", -1L)).thenReturn(true);
        assertError(() -> service.create(null, form(t("08:00"), t("17:00"), 0, null, null, false)),
                EAttendanceError.ATT_003);
    }

    @Test
    void otherCompanyShiftIsNotFound() {
        TestLogin.as(11L, "ATTENDANCE_VIEW");
        Shift s = new Shift();
        s.setId(1L);
        s.setCompanyId(10L);
        when(shifts.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(s));
        assertError(() -> service.get(1L), EAttendanceError.ATT_001);
    }

    @Test
    void summaryDayIsTotalMinusNight() {
        TestLogin.as(10L, "ATTENDANCE_VIEW");
        when(shifts.summary(10L)).thenReturn(List.<Object[]>of(new Object[]{5L, 4L, 2L}));
        var s = service.summary(null);
        assertThat(s.day()).isEqualTo(3);
        assertThat(s.active()).isEqualTo(4);
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertError(() -> service.options(null), EAttendanceError.ATT_004);
    }

    private static ShiftForm form(LocalTime start, LocalTime end, int breakMin, LocalTime bs, LocalTime be,
                                  boolean night) {
        return new ShiftForm("Morning", null, start, end, breakMin, bs, be, 10, night, null);
    }

    private static LocalTime t(String s) {
        return LocalTime.parse(s);
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
