package com.terp.erp.attendance.record;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.policy.AttendancePolicy;
import com.terp.erp.attendance.policy.AttendancePolicyRepository;
import com.terp.erp.attendance.shift.Shift;
import com.terp.erp.attendance.shift.ShiftRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Forgotten clock-outs are closed at the shift end (ERP manual §15, FR-ERP-ATT-003): a record with a clock-in, no
 * clock-out and a shift whose end + {@code after-end-minutes} has passed gets clock-out = the shift end and
 * autoClockedOut = true; hours and flags are computed as for a manual clock-out. The wait after the end lets people
 * who stay a little longer clock out themselves. Records without a shift are left open (no end to close at).
 * <p>
 * No ShedLock: several instances may run the job at once. Each row is closed by a conditional UPDATE … WHERE
 * clock_out IS NULL, so a row is closed once and a manual clock-out in between wins.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.attendance.auto-clock-out", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class AutoClockOutService {
    /** Days scanned back from today: a night shift started yesterday, plus a day of job downtime. */
    static final int LOOKBACK_DAYS = 2;

    private final AttendanceRecordRepository records;
    private final ShiftRepository shifts;
    private final AttendancePolicyRepository policies;
    private final Clock clock;
    @Value("${erp.attendance.auto-clock-out.after-end-minutes:60}")
    private long afterEndMinutes = 60;

    @Scheduled(cron = "${erp.attendance.auto-clock-out.cron:0 */15 * * * *}",
            zone = "${erp.attendance.auto-clock-out.zone:Asia/Ho_Chi_Minh}")
    @Transactional // the scheduler calls the proxy; run() below is a self-call
    @SchedulerLock(name = "attendance.auto-clock-out", lockAtMostFor = "PT14M")
    public void scheduled() {
        int closed = run();
        if (closed > 0) {
            log.info("Auto clock-out closed {} attendance record(s)", closed);
        }
    }

    /** Number of records this run closed. */
    @Transactional
    public int run() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(AttendanceRecordService.ZONE));
        int closed = 0;
        for (AttendanceRecord r : records.findByClockInIsNotNullAndClockOutIsNullAndShiftIdIsNotNullAndWorkDateBetween(
                now.toLocalDate().minusDays(LOOKBACK_DAYS), now.toLocalDate())) {
            Shift shift = shifts.findById(r.getShiftId())
                    .filter(s -> Objects.equals(s.getCompanyId(), r.getCompanyId())).orElse(null);
            if (shift == null) {
                continue;
            }
            // TODO(assumption): a shift whose end is at or before its start (night shift) ends on the next day; a
            // clock-in after midnight on such a shift (record dated the second day) is not told apart — confirm.
            LocalDateTime end = r.getWorkDate().atTime(shift.getEndTime());
            if (!shift.getEndTime().isAfter(shift.getStartTime())) {
                end = end.plusDays(1);
            }
            if (now.isBefore(end.plusMinutes(afterEndMinutes))) {
                continue;
            }
            closed += close(r, shift, end);
        }
        return closed;
    }

    /** Computes on a copy (the loaded entity is not changed, so nothing is flushed) and writes conditionally. */
    private int close(AttendanceRecord r, Shift shift, LocalDateTime end) {
        AttendanceRecord c = new AttendanceRecord();
        c.setHoliday(r.isHoliday());
        c.setClockIn(r.getClockIn());
        c.setClockOut(end.toLocalTime());
        AttendancePolicy policy = r.getAttendancePolicyId() == null ? null
                : policies.findById(r.getAttendancePolicyId())
                .filter(p -> Objects.equals(p.getCompanyId(), r.getCompanyId())).orElse(null);
        AttendanceCalculator.apply(c, shift, policy);
        return records.autoClockOut(r.getId(), c.getClockOut(), c.getTotalHours(), c.getBreakHours(),
                c.getOvertimeHours(), c.getOvertimeAmount(), c.isEarlyDeparture(), c.getStatus(), clock.instant());
    }

    void setAfterEndMinutes(long minutes) {
        this.afterEndMinutes = minutes;
    }
}
