package com.terp.erp.attendance.regularization;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.record.AttendanceRecord;
import com.terp.erp.attendance.record.AttendanceRecordService;
import com.terp.erp.attendance.regularization.RegularizationDtos.EmployeeRef;
import com.terp.erp.attendance.regularization.RegularizationDtos.RegularizationDto;
import com.terp.erp.attendance.regularization.RegularizationDtos.RegularizationForm;
import com.terp.erp.attendance.regularization.RegularizationDtos.StatusCounts;
import com.terp.erp.attendance.regularization.RegularizationDtos.Summary;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attendance Regularizations page (docs/analysis/06 §6.4.4). Admin/HR enter, approve and reject requests for now
 * (PO, 2026-11-19: employee self-service later). Approving writes the requested times into the record and computes its
 * flags again; rejecting leaves it unchanged. One pending request per record.
 * Only pending requests can be edited, approved, rejected or deleted (ATT_020; manual §20: edit/delete limited by
 * status) — a decided request stays as the audit trail of the record's change.
 */
@Service
@RequiredArgsConstructor
public class RegularizationService {
    private static final List<Long> NONE = List.of(-1L);

    private final RegularizationRepository requests;
    private final AttendanceRecordService records;
    private final EmployeeClient employees;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<RegularizationDto> search(Long requestedCompanyId, String keyword, Long employeeId, LocalDate from,
                                          LocalDate to, ERegularizationStatus status, Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        Map<Long, Employee> people = people(requestedCompanyId);
        String kw = Texts.kw(keyword);
        Page<Regularization> page = requests.search(companyId, employeeId, from, to, kw, matching(people, keyword),
                status, pageable);
        return page.map(g -> RegularizationDto.of(g, ref(people.get(g.getEmployeeId()), g.getEmployeeId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId, LocalDate from,
                                     LocalDate to) {
        Long companyId = company(requestedCompanyId);
        List<Long> ids = keyword == null || keyword.isBlank() ? NONE : matching(people(requestedCompanyId), keyword);
        Map<ERegularizationStatus, Long> by = counts(requests.countByStatus(companyId, employeeId, from, to,
                Texts.kw(keyword), ids));
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(),
                by.getOrDefault(ERegularizationStatus.PENDING, 0L), by.getOrDefault(ERegularizationStatus.APPROVED, 0L),
                by.getOrDefault(ERegularizationStatus.REJECTED, 0L));
    }

    @Transactional(readOnly = true)
    public Summary summary(Long requestedCompanyId) {
        Map<ERegularizationStatus, Long> by = counts(requests.summary(company(requestedCompanyId)));
        long approved = by.getOrDefault(ERegularizationStatus.APPROVED, 0L);
        long rejected = by.getOrDefault(ERegularizationStatus.REJECTED, 0L);
        long pending = by.getOrDefault(ERegularizationStatus.PENDING, 0L);
        return new Summary(approved + rejected + pending, pending, approved, rejected, rate(approved, rejected));
    }

    @Transactional(readOnly = true)
    public RegularizationDto get(Long id) {
        Regularization g = require(id);
        return dto(g);
    }

    @Transactional
    public RegularizationDto create(RegularizationForm form) {
        Regularization g = new Regularization();
        apply(g, form);
        return dto(requests.save(g));
    }

    /** Full replacement of a pending request; the original times are taken again from the chosen record. */
    @Transactional
    public RegularizationDto update(Long id, RegularizationForm form) {
        Regularization g = requirePending(id);
        apply(g, form);
        return dto(g);
    }

    @Transactional
    public RegularizationDto approve(Long id, String comments) {
        Regularization g = requirePending(id);
        AttendanceRecord r = records.require(g.getAttendanceRecordId());
        LocalTime in = g.getRequestedClockIn() != null ? g.getRequestedClockIn() : r.getClockIn();
        LocalTime out = g.getRequestedClockOut() != null ? g.getRequestedClockOut() : r.getClockOut();
        if (out != null && in == null) {
            throw ApiException.badRequest(EAttendanceError.ATT_010);
        }
        records.applyTimes(r, in, out);
        decide(g, ERegularizationStatus.APPROVED, comments);
        return dto(g);
    }

    @Transactional
    public RegularizationDto reject(Long id, String comments) {
        Regularization g = requirePending(id);
        decide(g, ERegularizationStatus.REJECTED, comments);
        return dto(g);
    }

    @Transactional
    public void delete(Long id) {
        requests.delete(requirePending(id));
    }

    private void apply(Regularization g, RegularizationForm f) {
        if (f.requestedClockIn() == null && f.requestedClockOut() == null) {
            throw ApiException.badRequest(EAttendanceError.ATT_022);
        }
        AttendanceRecord r = records.require(f.attendanceRecordId());
        if (!Objects.equals(r.getEmployeeId(), f.employeeId())) {
            throw ApiException.badRequest(EAttendanceError.ATT_021);
        }
        if (requests.existsByAttendanceRecordIdAndStatusAndIdNot(r.getId(), ERegularizationStatus.PENDING,
                g.getId() == null ? -1L : g.getId())) {
            throw ApiException.badRequest(EAttendanceError.ATT_019);
        }
        g.setCompanyId(r.getCompanyId());
        g.setEmployeeId(r.getEmployeeId());
        g.setAttendanceRecordId(r.getId());
        g.setWorkDate(r.getWorkDate());
        g.setOriginalClockIn(r.getClockIn());
        g.setOriginalClockOut(r.getClockOut());
        g.setRequestedClockIn(f.requestedClockIn());
        g.setRequestedClockOut(f.requestedClockOut());
        g.setReason(f.reason().trim());
    }

    private void decide(Regularization g, ERegularizationStatus status, String comments) {
        g.setStatus(status);
        g.setManagerComments(Texts.blankToNull(comments));
        g.setDecidedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        g.setDecidedAt(clock.instant());
    }

    private RegularizationDto dto(Regularization g) {
        Employee e = null;
        try {
            e = employees.get(g.getEmployeeId());
        } catch (RuntimeException ignored) {
            // A deleted employee still shows its requests, without a name.
        }
        return RegularizationDto.of(g, ref(e, g.getEmployeeId()));
    }

    private Map<Long, Employee> people(Long requestedCompanyId) {
        return employees.all(requestedCompanyId).stream()
                .collect(Collectors.toMap(e -> Long.valueOf(e.id()), Function.identity(), (a, b) -> a));
    }

    /** Ids of the employees whose name, number, code or email contains the keyword. */
    static List<Long> matching(Map<Long, Employee> people, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return NONE;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        List<Long> ids = people.entrySet().stream().filter(en -> {
            Employee e = en.getValue();
            return contains(e.fullName(), k) || contains(e.employeeNo(), k) || contains(e.employeeCode(), k)
                    || contains(e.email(), k);
        }).map(Map.Entry::getKey).toList();
        return ids.isEmpty() ? NONE : ids;
    }

    private static boolean contains(String s, String k) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(k);
    }

    private static EmployeeRef ref(Employee e, Long id) {
        return e == null ? new EmployeeRef(id.toString(), null, null)
                : new EmployeeRef(e.id(), e.fullName(), e.profileImageUrl());
    }

    private static Map<ERegularizationStatus, Long> counts(List<Object[]> rows) {
        Map<ERegularizationStatus, Long> by = new EnumMap<>(ERegularizationStatus.class);
        for (Object[] row : rows) {
            by.put((ERegularizationStatus) row[0], (Long) row[1]);
        }
        return by;
    }

    /** TODO(assumption): "overall approval rate" = approved / decided requests (pending ones are not counted). */
    static BigDecimal rate(long approved, long rejected) {
        long decided = approved + rejected;
        return decided == 0 ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(approved * 100.0 / decided).setScale(1, RoundingMode.HALF_UP);
    }

    private Regularization require(Long id) {
        Regularization g = requests.findById(id).orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_018));
        TenantGuard.assertSameCompany(g.getCompanyId(), EAttendanceError.ATT_018);
        return g;
    }

    private Regularization requirePending(Long id) {
        Regularization g = require(id);
        if (g.getStatus() != ERegularizationStatus.PENDING) {
            throw ApiException.badRequest(EAttendanceError.ATT_020);
        }
        return g;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
