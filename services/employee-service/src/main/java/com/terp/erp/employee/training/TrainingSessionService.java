package com.terp.erp.employee.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.EmployeeExit;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.training.TrainingSession.ELocationType;
import com.terp.erp.employee.training.TrainingSession.EStatus;
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Training Sessions page and calendar (docs/analysis/06 §7.4.2). A new session needs a draft or active program
 * (EMP_083); trainers are employees of the company; end after start (EMP_052). A recurring session is saved as
 * copies sharing a series id (PO, 2026-11-20). Attendance lists the program's trainees (PO, 2026-11-20).
 */
@Service
@RequiredArgsConstructor
public class TrainingSessionService {
    static final int MAX_CALENDAR_DAYS = 93;
    private static final Set<TrainingProgram.EStatus> OPEN = EnumSet.of(TrainingProgram.EStatus.DRAFT,
            TrainingProgram.EStatus.ACTIVE);

    private final TrainingSessionRepository sessions;
    private final TrainingAttendanceRepository attendance;
    private final EmployeeTrainingRepository trainings;
    private final TrainingProgramRepository programRepo;
    private final TrainingProgramService programs;
    private final LifecycleSupport support;

    public enum EFrequency { DAILY, WEEKLY, MONTHLY }

    public record ProgramInfo(String id, String name, BigDecimal durationHours, BigDecimal cost, Integer capacity) {
    }

    /** status null = not marked yet. */
    public record AttendanceDto(EmployeeRef employee, TrainingAttendance.EStatus status, String notes) {
    }

    /** durationMinutes = end − start; attendance (list of trainees) and program info only on the details. */
    public record SessionDto(String id, ProgramInfo program, String name, LocalDateTime startAt, LocalDateTime endAt,
                             long durationMinutes, ELocationType locationType, String location, String meetingLink,
                             EStatus status, String notes, String seriesId, List<EmployeeRef> trainers,
                             long attendees, List<AttendanceDto> attendance, String createdBy, Instant createdDate,
                             Instant updatedDate) {
    }

    public record CalendarItem(String id, String name, Ref program, LocalDateTime startAt, LocalDateTime endAt,
                               EStatus status) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record Recurrence(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EFrequency frequency,
            @Schema(description = "Number of sessions including the first, 2–52", example = "4",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(2) @Max(52) Integer count) {
    }

    public record SessionForm(
            @Schema(description = "Entry of /training/programs", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long programId,
            @Schema(example = "Module 1: Basics", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String name,
            @Schema(example = "2026-12-01T09:00:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDateTime startAt,
            @Schema(example = "2026-12-01T12:00:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDateTime endAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ELocationType locationType,
            @Schema(example = "Room 3") @Size(max = 300) String location,
            @Schema(example = "https://meet.example.invalid/abc") @Size(max = 500) String meetingLink,
            @Schema(description = "Omitted = SCHEDULED on create, kept on edit") EStatus status,
            @Size(max = 5000) String notes,
            @Schema(description = "Employee ids; full set") Set<@NotNull Long> trainerIds,
            @Schema(description = "Create only: repeat the session; ignored on edit") @Valid Recurrence recurrence) {
    }

    public record AttendanceItem(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "null = clear the mark") TrainingAttendance.EStatus status,
            @Size(max = 1000) String notes) {
    }

    public record AttendanceForm(@NotEmpty List<@Valid @NotNull AttendanceItem> items) {
    }

    /** The first session and the number created (more than one when recurring). */
    public record CreateResult(SessionDto session, int created, String seriesId) {
    }

    @Transactional(readOnly = true)
    public Page<SessionDto> search(Long requestedCompanyId, String keyword, Long programId, ELocationType locationType,
                                   LocalDate from, LocalDate to, EStatus status, Pageable pageable) {
        Page<TrainingSession> page = sessions.search(TrainingSupport.company(requestedCompanyId), programId,
                locationType, start(from), end(to), status, DepartmentService.kw(keyword), pageable);
        Lookups l = lookups(page.getContent());
        return page.map(s -> dto(s, l, null));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long programId,
                                     ELocationType locationType, LocalDate from, LocalDate to) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : sessions.countByStatus(TrainingSupport.company(requestedCompanyId), programId,
                locationType, start(from), end(to), DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Calendar View: sessions overlapping [from, to] (dates inclusive), at most {@value #MAX_CALENDAR_DAYS} days. */
    @Transactional(readOnly = true)
    public List<CalendarItem> calendar(Long requestedCompanyId, LocalDate from, LocalDate to, Long programId,
                                       EStatus status) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "end date", "start date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(EEmployeeError.EMP_088, MAX_CALENDAR_DAYS);
        }
        List<TrainingSession> list = sessions.calendar(TrainingSupport.company(requestedCompanyId), start(from),
                end(to), programId, status);
        Map<Long, String> names = programRepo.findAllById(list.stream().map(TrainingSession::getProgramId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(TrainingProgram::getId,
                TrainingProgram::getName));
        return list.stream().map(s -> new CalendarItem(s.getId().toString(), s.getName(),
                new Ref(s.getProgramId().toString(), names.get(s.getProgramId())), s.getStartAt(), s.getEndAt(),
                s.getStatus())).toList();
    }

    @Transactional(readOnly = true)
    public SessionDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public CreateResult create(SessionForm form) {
        TrainingSession first = new TrainingSession();
        apply(first, form, true);
        Recurrence rec = form.recurrence();
        if (rec == null) {
            return new CreateResult(detail(sessions.save(first)), 1, null);
        }
        String series = UUID.randomUUID().toString();
        first.setSeriesId(series);
        sessions.save(first);
        for (int i = 1; i < rec.count(); i++) {
            TrainingSession copy = new TrainingSession();
            copy.setCompanyId(first.getCompanyId());
            copy.setProgramId(first.getProgramId());
            copy.setName(first.getName());
            copy.setStartAt(shift(first.getStartAt(), rec.frequency(), i));
            copy.setEndAt(shift(first.getEndAt(), rec.frequency(), i));
            copy.setLocationType(first.getLocationType());
            copy.setLocation(first.getLocation());
            copy.setMeetingLink(first.getMeetingLink());
            copy.setStatus(first.getStatus());
            copy.setNotes(first.getNotes());
            copy.setSeriesId(series);
            copy.getTrainerIds().addAll(first.getTrainerIds());
            sessions.save(copy);
        }
        return new CreateResult(detail(first), rec.count(), series);
    }

    static LocalDateTime shift(LocalDateTime t, EFrequency f, int n) {
        return switch (f) {
            case DAILY -> t.plusDays(n);
            case WEEKLY -> t.plusWeeks(n);
            case MONTHLY -> t.plusMonths(n);
        };
    }

    /** Full replacement of this session only (not the rest of its series); status omitted = kept. */
    @Transactional
    public SessionDto update(Long id, SessionForm form) {
        TrainingSession s = require(id);
        apply(s, form, !form.programId().equals(s.getProgramId()));
        return detail(s);
    }

    /** Manage Attendance: marks (or clears) the listed trainees. */
    @Transactional
    public SessionDto updateAttendance(Long id, List<AttendanceItem> items) {
        TrainingSession s = require(id);
        Set<Long> trainees = trainings.trainees(s.getProgramId()).stream().map(EmployeeTraining::getEmployeeId)
                .collect(Collectors.toSet());
        Map<Long, TrainingAttendance> rows = attendance.findBySessionId(s.getId()).stream()
                .collect(Collectors.toMap(TrainingAttendance::getEmployeeId, Function.identity()));
        for (AttendanceItem x : items) {
            if (!trainees.contains(x.employeeId())) {
                throw ApiException.badRequest(EEmployeeError.EMP_087);
            }
            TrainingAttendance row = rows.get(x.employeeId());
            if (x.status() == null) {
                if (row != null) {
                    attendance.delete(row);
                    rows.remove(x.employeeId());
                }
                continue;
            }
            if (row == null) {
                row = new TrainingAttendance();
                row.setSessionId(s.getId());
                row.setEmployeeId(x.employeeId());
                rows.put(x.employeeId(), row);
            }
            row.setStatus(x.status());
            row.setNotes(TrainingSupport.blankToNull(x.notes()));
            attendance.save(row);
        }
        attendance.flush();
        return detail(s);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(TrainingSession s, SessionForm f, boolean newProgram) {
        // A new session takes the program's company (a super admin names no company here).
        TrainingProgram p = s.getCompanyId() == null ? programs.require(f.programId())
                : programs.usable(f.programId(), s.getCompanyId());
        if (newProgram && !OPEN.contains(p.getStatus())) {
            throw ApiException.badRequest(EEmployeeError.EMP_083);
        }
        if (!f.endAt().isAfter(f.startAt())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "end", "start");
        }
        Set<Long> trainers = new LinkedHashSet<>(f.trainerIds() == null ? Set.of() : f.trainerIds());
        for (Long id : trainers) {
            Employee e = support.employee(id);
            if (!e.getCompanyId().equals(p.getCompanyId())) {
                throw ApiException.notFound(EEmployeeError.EMP_001);
            }
            if (!s.getTrainerIds().contains(id)) {
                EmployeeExit.assertEmployed(e);
            }
        }
        s.setCompanyId(p.getCompanyId());
        s.setProgramId(p.getId());
        s.setName(f.name().trim());
        s.setStartAt(f.startAt());
        s.setEndAt(f.endAt());
        s.setLocationType(f.locationType());
        s.setLocation(TrainingSupport.blankToNull(f.location()));
        s.setMeetingLink(TrainingSupport.blankToNull(f.meetingLink()));
        if (f.status() != null) {
            s.setStatus(f.status());
        }
        s.setNotes(TrainingSupport.blankToNull(f.notes()));
        s.getTrainerIds().clear();
        s.getTrainerIds().addAll(trainers);
    }

    private static LocalDateTime start(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay();
    }

    private record Lookups(Map<Long, TrainingProgram> programs, Map<Long, EmployeeRef> people,
                           Map<Long, Long> present) {
    }

    private Lookups lookups(List<TrainingSession> list) {
        Set<Long> programIds = new HashSet<>();
        Set<Long> people = new HashSet<>();
        for (TrainingSession s : list) {
            programIds.add(s.getProgramId());
            people.addAll(s.getTrainerIds());
        }
        Map<Long, Long> present = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] r : attendance.presentBySession(list.stream().map(TrainingSession::getId).toList())) {
                present.put((Long) r[0], (Long) r[1]);
            }
        }
        return new Lookups(programRepo.findAllById(programIds).stream()
                .collect(Collectors.toMap(TrainingProgram::getId, Function.identity())), support.refs(people),
                present);
    }

    private SessionDto detail(TrainingSession s) {
        Lookups l = lookups(List.of(s));
        Map<Long, TrainingAttendance> marks = attendance.findBySessionId(s.getId()).stream()
                .collect(Collectors.toMap(TrainingAttendance::getEmployeeId, Function.identity()));
        Set<Long> trainees = new LinkedHashSet<>();
        trainings.trainees(s.getProgramId()).forEach(t -> trainees.add(t.getEmployeeId()));
        trainees.addAll(marks.keySet());
        Map<Long, EmployeeRef> refs = support.refs(trainees);
        List<AttendanceDto> rows = new ArrayList<>();
        for (Long id : trainees) {
            TrainingAttendance a = marks.get(id);
            rows.add(new AttendanceDto(refs.get(id), a == null ? null : a.getStatus(), a == null ? null
                    : a.getNotes()));
        }
        rows.sort(Comparator.comparing((AttendanceDto a) -> a.employee() == null ? "" : a.employee().fullName(),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return dto(s, l, rows);
    }

    private static SessionDto dto(TrainingSession s, Lookups l, List<AttendanceDto> rows) {
        TrainingProgram p = l.programs().get(s.getProgramId());
        ProgramInfo program = p == null ? new ProgramInfo(s.getProgramId().toString(), null, null, null, null)
                : new ProgramInfo(p.getId().toString(), p.getName(), p.getDurationHours(), p.getCost(),
                p.getCapacity());
        List<EmployeeRef> trainers = s.getTrainerIds().stream().map(l.people()::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(EmployeeRef::fullName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new SessionDto(s.getId().toString(), program, s.getName(), s.getStartAt(), s.getEndAt(),
                Duration.between(s.getStartAt(), s.getEndAt()).toMinutes(), s.getLocationType(), s.getLocation(),
                s.getMeetingLink(), s.getStatus(), s.getNotes(), s.getSeriesId(), trainers,
                l.present().getOrDefault(s.getId(), 0L), rows, s.getCreatedBy(), s.getCreatedDate(),
                s.getUpdatedDate());
    }

    private TrainingSession require(Long id) {
        TrainingSession s = sessions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_086));
        TenantGuard.assertSameCompany(s.getCompanyId(), EEmployeeError.EMP_086);
        return s;
    }
}
