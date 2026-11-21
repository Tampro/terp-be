package com.terp.erp.attendance.shift;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.shift.ShiftDtos.ShiftDto;
import com.terp.erp.attendance.shift.ShiftDtos.ShiftForm;
import com.terp.erp.attendance.shift.ShiftDtos.ShiftOption;
import com.terp.erp.attendance.shift.ShiftDtos.StatusCounts;
import com.terp.erp.attendance.shift.ShiftDtos.Summary;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.LocalTime;
import java.util.List;

/**
 * Shifts page (docs/analysis/06 §6.4.5). A shift that ends at or before its start runs over midnight and must be a
 * night shift (ATT_005); the break, when timed, lies between its start and end (ATT_006) and never exceeds the shift
 * (ATT_007). TODO(assumption): deleting a shift still set on employee profiles is not checked yet (employee-service
 * holds shift_id); a removed shift simply stops appearing — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class ShiftService {
    private final ShiftRepository shifts;

    @Transactional(readOnly = true)
    public Page<ShiftDto> search(Long requestedCompanyId, String keyword, Boolean nightShift, EStatus status,
                                 Pageable pageable) {
        return shifts.search(company(requestedCompanyId), nightShift, status, Texts.kw(keyword), pageable)
                .map(ShiftDto::of);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Boolean nightShift) {
        long active = 0;
        long inactive = 0;
        for (Object[] row : shifts.countByStatus(company(requestedCompanyId), nightShift, Texts.kw(keyword))) {
            if (row[0] == EStatus.ACTIVE) {
                active = (Long) row[1];
            } else {
                inactive = (Long) row[1];
            }
        }
        return new StatusCounts(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    public Summary summary(Long requestedCompanyId) {
        Object[] r = shifts.summary(company(requestedCompanyId)).get(0);
        long total = ((Number) r[0]).longValue();
        long night = ((Number) r[2]).longValue();
        return new Summary(total, ((Number) r[1]).longValue(), night, total - night);
    }

    @Transactional(readOnly = true)
    public List<ShiftOption> options(Long requestedCompanyId) {
        return shifts.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(company(requestedCompanyId), EStatus.ACTIVE)
                .stream().map(s -> new ShiftOption(s.getId().toString(), s.getName(), s.getStartTime(), s.getEndTime(),
                        s.isNightShift())).toList();
    }

    @Transactional(readOnly = true)
    public ShiftDto get(Long id) {
        return ShiftDto.of(require(id));
    }

    @Transactional
    public ShiftDto create(Long requestedCompanyId, ShiftForm form) {
        Shift s = new Shift();
        s.setCompanyId(company(requestedCompanyId));
        apply(s, form, -1L);
        return ShiftDto.of(shifts.save(s));
    }

    /** Full replacement: omitted description and break times are cleared, omitted status is kept. */
    @Transactional
    public ShiftDto update(Long id, ShiftForm form) {
        Shift s = require(id);
        apply(s, form, id);
        return ShiftDto.of(s);
    }

    @Transactional
    public ShiftDto changeStatus(Long id, EStatus status) {
        Shift s = require(id);
        s.setStatus(status);
        return ShiftDto.of(s);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    static void validate(ShiftForm f) {
        if (!f.endTime().isAfter(f.startTime()) && !f.nightShift()) {
            throw ApiException.badRequest(EAttendanceError.ATT_005);
        }
        LocalTime bs = f.breakStartTime();
        LocalTime be = f.breakEndTime();
        if ((bs == null) != (be == null)) {
            throw ApiException.badRequest(EAttendanceError.ATT_006);
        }
        long span = Shift.spanMinutes(f.startTime(), f.endTime());
        if (bs != null) {
            // Break offsets from the shift start, so a night shift's break after midnight is still "inside".
            long from = Shift.spanMinutes(f.startTime(), bs) % (24 * 60);
            long to = Shift.spanMinutes(f.startTime(), be) % (24 * 60);
            if (to <= from || to > span) {
                throw ApiException.badRequest(EAttendanceError.ATT_006);
            }
        }
        if (f.breakDurationMinutes() >= span) {
            throw ApiException.badRequest(EAttendanceError.ATT_007);
        }
    }

    private void apply(Shift s, ShiftForm f, Long selfId) {
        validate(f);
        String name = f.name().trim();
        if (shifts.nameTaken(s.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EAttendanceError.ATT_003);
        }
        s.setName(name);
        s.setDescription(Texts.blankToNull(f.description()));
        s.setStartTime(f.startTime());
        s.setEndTime(f.endTime());
        s.setBreakDurationMinutes(f.breakDurationMinutes());
        s.setBreakStartTime(f.breakStartTime());
        s.setBreakEndTime(f.breakEndTime());
        s.setGracePeriodMinutes(f.gracePeriodMinutes());
        s.setNightShift(f.nightShift());
        if (f.status() != null) {
            s.setStatus(f.status());
        }
    }

    private Shift require(Long id) {
        Shift s = shifts.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_001));
        TenantGuard.assertSameCompany(s.getCompanyId(), EAttendanceError.ATT_001);
        return s;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
