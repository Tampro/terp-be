package com.terp.erp.attendance.summary;

import org.junit.jupiter.api.Test;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.client.EmployeeClient.Ref;
import com.terp.erp.attendance.leave.LeaveApplication;
import com.terp.erp.attendance.record.AttendanceRecord;
import com.terp.erp.attendance.record.ERecordStatus;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollSummaryTest {
    // November 2026 starts on a Sunday: 21 weekdays.
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);
    private static final EnumSet<DayOfWeek> OFF = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    @Test
    void countsWorkingPresentLeaveLopAndOvertime() {
        List<Holiday> holidays = List.of(
                new Holiday("1", "All", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), true, List.of()),
                new Holiday("2", "Other branch", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), false,
                        List.of(new Ref("99", "B99"))));
        List<AttendanceRecord> recs = List.of(
                record(4, ERecordStatus.PRESENT, "1.50", "150000"),
                record(5, ERecordStatus.HALF_DAY, "0", "0"),
                record(6, ERecordStatus.ABSENT, "0", "0"),
                record(9, ERecordStatus.ABSENT, "0", "0"), // covered by unpaid leave below: not LOP
                record(10, ERecordStatus.ON_LEAVE, "0", "0"));
        List<LeaveApplication> leave = List.of(
                leave(1L, 9, 9),
                leave(2L, 10, 12),   // paid, 3 working days
                leave(1L, 14, 16));  // unpaid, 14-15 is a weekend: 1 working day
        var s = PayrollSummaryService.of(7L, FROM, TO, OFF, holidays, 1L, recs, leave, Map.of(1L, false, 2L, true));
        assertThat(s.workingDays()).isEqualTo(20); // 21 weekdays - the all-branch holiday
        assertThat(s.presentDays()).isEqualByComparingTo("1.5");
        assertThat(s.paidLeaveDays()).isEqualByComparingTo("3");
        assertThat(s.unpaidLeaveDays()).isEqualByComparingTo("2");
        assertThat(s.lopDays()).isEqualByComparingTo("1.5");
        assertThat(s.overtimeHours()).isEqualByComparingTo("1.5");
        assertThat(s.overtimeAmount()).isEqualByComparingTo("150000");
        assertThat(s.records()).hasSize(5);
        assertThat(s.records().get(0).date()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    private static AttendanceRecord record(int day, ERecordStatus status, String otHours, String otAmount) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(7L);
        r.setWorkDate(LocalDate.of(2026, 11, day));
        r.setStatus(status);
        r.setOvertimeHours(new BigDecimal(otHours));
        r.setOvertimeAmount(new BigDecimal(otAmount));
        return r;
    }

    private static LeaveApplication leave(Long type, int start, int end) {
        LeaveApplication a = new LeaveApplication();
        a.setEmployeeId(7L);
        a.setLeaveTypeId(type);
        a.setStartDate(LocalDate.of(2026, 11, start));
        a.setEndDate(LocalDate.of(2026, 11, end));
        return a;
    }
}
