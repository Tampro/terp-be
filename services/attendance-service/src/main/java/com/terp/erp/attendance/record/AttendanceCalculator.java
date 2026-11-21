package com.terp.erp.attendance.record;

import com.terp.erp.attendance.policy.AttendancePolicy;
import com.terp.erp.attendance.shift.Shift;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * Flags and hours of a record (PO, 2026-11-19: computed from the employee's shift and policy).
 * <ul>
 *   <li>holiday box ticked → HOLIDAY; no clock-in → ABSENT;</li>
 *   <li>worked = clock-in to clock-out (next day when the out is before the in), minus the shift break when the day
 *       is longer than the break; no clock-out yet → 0 h;</li>
 *   <li>Late: clock-in after shift start + max(shift grace, policy late grace);</li>
 *   <li>Early Departure: clock-out before shift end − policy early grace;</li>
 *   <li>Overtime: worked beyond the shift's working time, paid at the policy rate;</li>
 *   <li>Half Day: worked below the policy threshold (4 h without a policy).</li>
 * </ul>
 * Without a shift only the hours and Half Day apply. TODO(assumption): the break is deducted as a block whatever the
 * actual break times — confirm with BA.
 */
final class AttendanceCalculator {
    static final BigDecimal DEFAULT_HALF_DAY_HOURS = new BigDecimal("4.00");
    private static final int DAY = 24 * 60;

    private AttendanceCalculator() {
    }

    static void apply(AttendanceRecord r, Shift shift, AttendancePolicy policy) {
        r.setLate(false);
        r.setEarlyDeparture(false);
        r.setAbsent(false);
        r.setTotalHours(BigDecimal.ZERO);
        r.setBreakHours(BigDecimal.ZERO);
        r.setOvertimeHours(BigDecimal.ZERO);
        r.setOvertimeAmount(BigDecimal.ZERO);
        if (r.isHoliday()) {
            r.setStatus(ERecordStatus.HOLIDAY);
            if (r.getClockIn() == null) {
                return;
            }
        } else if (r.getClockIn() == null) {
            r.setAbsent(true);
            r.setStatus(ERecordStatus.ABSENT);
            return;
        }
        long span = r.getClockOut() == null ? 0 : Shift.spanMinutes(r.getClockIn(), r.getClockOut());
        long breakMin = shift != null && span > shift.getBreakDurationMinutes() ? shift.getBreakDurationMinutes() : 0;
        long worked = span - breakMin;
        r.setTotalHours(hours(worked));
        r.setBreakHours(hours(breakMin));
        if (shift != null) {
            int lateGrace = Math.max(shift.getGracePeriodMinutes(), policy == null ? 0 : policy.getLateArrivalGraceMinutes());
            r.setLate(offset(shift.getStartTime(), r.getClockIn()) > lateGrace);
            if (r.getClockOut() != null) {
                int earlyGrace = policy == null ? 0 : policy.getEarlyDepartureGraceMinutes();
                r.setEarlyDeparture(offset(shift.getEndTime(), r.getClockOut()) < -earlyGrace);
                long overtime = Math.max(0, worked - shift.workingMinutes());
                r.setOvertimeHours(hours(overtime));
                if (policy != null) {
                    r.setOvertimeAmount(hours(overtime).multiply(policy.getOvertimeRatePerHour())
                            .setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        if (r.isHoliday()) {
            return;
        }
        BigDecimal threshold = policy == null ? DEFAULT_HALF_DAY_HOURS : policy.getHalfDayThresholdHours();
        r.setStatus(r.getClockOut() != null && r.getTotalHours().compareTo(threshold) < 0
                ? ERecordStatus.HALF_DAY : ERecordStatus.PRESENT);
    }

    /** Minutes of t after the reference time, in -12 h .. +12 h (a night shift crosses midnight). */
    static long offset(LocalTime reference, LocalTime t) {
        long m = Math.floorMod(t.toSecondOfDay() / 60 - reference.toSecondOfDay() / 60, DAY);
        return m > DAY / 2 ? m - DAY : m;
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
