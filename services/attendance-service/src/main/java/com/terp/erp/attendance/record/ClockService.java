package com.terp.erp.attendance.record;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.policy.AttendancePolicy;
import com.terp.erp.attendance.policy.AttendancePolicyRepository;
import com.terp.erp.attendance.record.RecordDtos.RecordDto;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.attendance.shift.Shift;
import com.terp.erp.attendance.shift.ShiftRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Self clock-in / clock-out of the caller's own employee profile (ERP manual §15, FR-ERP-ATT-003/004): once per day,
 * from an allowed IP when the company restricts it. Times are the wall clock of {@link AttendanceRecordService#ZONE},
 * to the minute; the flags and hours come from the same AttendanceCalculator as a typed record.
 */
@Service
@RequiredArgsConstructor
public class ClockService {
    private final AttendanceRecordRepository records;
    private final ShiftRepository shifts;
    private final AttendancePolicyRepository policies;
    private final AttendanceSettingService settings;
    private final EmployeeClient employees;
    private final Clock clock;

    public record ClockForm(@Schema(example = "Working from the client office") @Size(max = 500) String notes) {
    }

    public record ShiftDto(String id, String name, LocalTime startTime, LocalTime endTime, int gracePeriodMinutes,
                           boolean nightShift) {
        static ShiftDto of(Shift s) {
            return s == null ? null : new ShiftDto(s.getId().toString(), s.getName(), s.getStartTime(), s.getEndTime(),
                    s.getGracePeriodMinutes(), s.isNightShift());
        }
    }

    /** record null before the first clock-in; the open record of last night for a night shift still running. */
    public record TodayDto(LocalDate date, RecordDto record, ShiftDto shift, boolean canClockIn, boolean canClockOut) {
    }

    @Transactional(readOnly = true)
    public TodayDto today() {
        Employee e = me();
        Long companyId = Long.valueOf(e.companyId());
        Long employeeId = Long.valueOf(e.id());
        LocalDate today = now().toLocalDate();
        Optional<AttendanceRecord> open = openRecord(employeeId, today);
        AttendanceRecord r = open.or(() -> records.findByEmployeeIdAndWorkDate(employeeId, today)).orElse(null);
        Shift shift = r != null && r.getShiftId() != null ? ownShift(companyId, r.getShiftId().toString())
                : ownShift(companyId, e.shiftId());
        boolean clockedInToday = records.findByEmployeeIdAndWorkDate(employeeId, today)
                .map(x -> x.getClockIn() != null).orElse(false);
        return new TodayDto(today, r == null ? null : RecordDto.of(r), ShiftDto.of(shift), !clockedInToday,
                open.isPresent());
    }

    @Transactional
    public RecordDto clockIn(ClockForm form, String clientIp) {
        Employee e = me();
        Long companyId = Long.valueOf(e.companyId());
        settings.assertClockIpAllowed(companyId, clientIp);
        Long employeeId = Long.valueOf(e.id());
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();
        AttendanceRecord r = records.findByEmployeeIdAndWorkDate(employeeId, today).orElse(null);
        if (r != null && r.getClockIn() != null) {
            throw ApiException.badRequest(EAttendanceError.ATT_055);
        }
        if (r == null) {
            r = new AttendanceRecord();
            r.setCompanyId(companyId);
            r.setEmployeeId(employeeId);
            r.setWorkDate(today);
        }
        // TODO(assumption): clocking in on a day already marked ON_LEAVE / Absent turns it into a worked day; the
        // holiday box is not ticked automatically on a public holiday — confirm with BA.
        r.setWeekend(settings.offDays(companyId).contains(today.getDayOfWeek()));
        r.setClockIn(now.toLocalTime());
        r.setClockOut(null);
        r.setAutoClockedOut(false);
        r.setNotes(Texts.blankToNull(form == null ? null : form.notes()));
        Shift shift = ownShift(companyId, e.shiftId());
        AttendancePolicy policy = ownPolicy(companyId, e.attendancePolicyId());
        r.setShiftId(shift == null ? null : shift.getId());
        r.setAttendancePolicyId(policy == null ? null : policy.getId());
        AttendanceCalculator.apply(r, shift, policy);
        return RecordDto.of(records.save(r));
    }

    @Transactional
    public RecordDto clockOut(ClockForm form, String clientIp) {
        Employee e = me();
        Long companyId = Long.valueOf(e.companyId());
        settings.assertClockIpAllowed(companyId, clientIp);
        LocalDateTime now = now();
        AttendanceRecord r = openRecord(Long.valueOf(e.id()), now.toLocalDate())
                .orElseThrow(() -> ApiException.badRequest(EAttendanceError.ATT_056));
        TenantGuard.assertSameCompany(r.getCompanyId(), EAttendanceError.ATT_008);
        r.setClockOut(now.toLocalTime());
        String notes = Texts.blankToNull(form == null ? null : form.notes());
        if (notes != null) {
            r.setNotes(notes);
        }
        // The shift and policy kept on the record, as for a regularization.
        Shift shift = r.getShiftId() == null ? null : ownShift(r.getCompanyId(), r.getShiftId().toString());
        AttendancePolicy policy = r.getAttendancePolicyId() == null ? null
                : ownPolicy(r.getCompanyId(), r.getAttendancePolicyId().toString());
        AttendanceCalculator.apply(r, shift, policy);
        return RecordDto.of(records.save(r));
    }

    /**
     * Today's record with a clock-in and no clock-out; otherwise yesterday's, when it is on a night shift (the
     * clock-out after midnight belongs to the day the shift started).
     */
    private Optional<AttendanceRecord> openRecord(Long employeeId, LocalDate today) {
        Optional<AttendanceRecord> r = records.findByEmployeeIdAndWorkDate(employeeId, today).filter(ClockService::isOpen);
        if (r.isPresent()) {
            return r;
        }
        return records.findByEmployeeIdAndWorkDate(employeeId, today.minusDays(1)).filter(ClockService::isOpen)
                .filter(x -> x.getShiftId() != null && shifts.findById(x.getShiftId())
                        .filter(s -> Objects.equals(s.getCompanyId(), x.getCompanyId()))
                        .map(Shift::isNightShift).orElse(false));
    }

    private static boolean isOpen(AttendanceRecord r) {
        return r.getClockIn() != null && r.getClockOut() == null;
    }

    /** The caller's own profile, in the caller's own company (ATT_004 for a super admin, ATT_060 without one). */
    private Employee me() {
        Long companyId = TenantGuard.resolveCompany(null, EAttendanceError.ATT_004);
        Employee e = employees.me();
        if (e == null || e.id() == null || e.companyId() == null) {
            throw ApiException.notFound(EAttendanceError.ATT_060);
        }
        if (!Objects.equals(Long.valueOf(e.companyId()), companyId)) {
            throw ApiException.notFound(EAttendanceError.ATT_060);
        }
        return e;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(AttendanceRecordService.ZONE)).truncatedTo(ChronoUnit.MINUTES);
    }

    private Shift ownShift(Long companyId, String id) {
        return id == null ? null : shifts.findById(Long.valueOf(id))
                .filter(s -> Objects.equals(s.getCompanyId(), companyId)).orElse(null);
    }

    private AttendancePolicy ownPolicy(Long companyId, String id) {
        return id == null ? null : policies.findById(Long.valueOf(id))
                .filter(p -> Objects.equals(p.getCompanyId(), companyId)).orElse(null);
    }
}
