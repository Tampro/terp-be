package com.terp.erp.employee.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.EmployeeExit;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.DesignationRepository;
import com.terp.erp.employee.training.EmployeeTraining.EStatus;
import com.terp.erp.employee.training.TrainingProgramService.ProgramDto;
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Employee Trainings page (docs/analysis/06 §7.4.1): assign, bulk assign, dashboard. A new assignment needs a draft or
 * active program (EMP_083) with a free seat (EMP_084; failed trainings free their seat) and no other assignment of the
 * same employee to it (EMP_085). COMPLETED without a completion date takes today.
 */
@Service
@RequiredArgsConstructor
public class EmployeeTrainingService {
    /** TODO(assumption): "today" is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<TrainingProgram.EStatus> OPEN = EnumSet.of(TrainingProgram.EStatus.DRAFT,
            TrainingProgram.EStatus.ACTIVE);

    private final EmployeeTrainingRepository trainings;
    private final TrainingProgramRepository programRepo;
    private final TrainingTypeRepository typeRepo;
    private final TrainingProgramService programs;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final LifecycleSupport support;
    private final Clock clock;
    private final AssessmentResultRepository results;
    private final TrainingAssessmentService assessments;

    public enum EResult { PASSED, FAILED }

    public record ProgramRef(String id, String name, String trainingType) {
    }

    /** The Employee Details panel. */
    public record EmployeeDetails(String id, String employeeNo, String fullName, String email, String phone,
                                  String department, String designation, String profileImageUrl) {
    }

    /** certificate, employeeDetails, programDetails and assessmentResults only on the details. */
    public record TrainingDto(String id, EmployeeRef employee, ProgramRef program, EStatus status,
                              LocalDate assignedDate, LocalDate completionDate, EmployeeRef assignedBy,
                              BigDecimal score, Boolean passed, EResult result, String feedback, String notes,
                              String certificateId, FileRef certificate, EmployeeDetails employeeDetails,
                              ProgramDto programDetails,
                              List<TrainingAssessmentService.EmployeeTrainingResult> assessmentResults,
                              Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record TrainingForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Entry of /training/programs", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long programId,
            @Schema(description = "Omitted = ASSIGNED on create, kept on edit") EStatus status,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate assignedDate,
            @Schema(description = "Omitted with COMPLETED = today") LocalDate completionDate,
            @Schema(description = "Media library file id of the certificate") Long certificateMediaId,
            @Schema(description = "0–100 (%)", example = "85") @DecimalMin("0") @DecimalMax("100")
            @Digits(integer = 3, fraction = 2) BigDecimal score,
            @Schema(description = "null = no result yet") Boolean passed,
            @Size(max = 5000) String feedback,
            @Size(max = 5000) String notes) {
    }

    public record BulkForm(
            @Schema(example = "[\"1\",\"2\"]", requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty
            @Size(max = 200) Set<@NotNull Long> employeeIds,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long programId,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate assignedDate,
            @Size(max = 5000) String notes) {
    }

    /** skippedEmployeeIds = already assigned to the program. */
    public record BulkResult(int created, List<String> skippedEmployeeIds) {
    }

    public record ProgramCompletion(Ref program, long completed, long total, int rate) {
    }

    /** completionRate = completed / total, %. */
    public record Dashboard(long total, long completed, int completionRate, long inProgress, long assigned,
                            long failed, List<ProgramCompletion> programCompletion,
                            List<TrainingDto> recentCompletions, List<TrainingDto> upcoming) {
    }

    @Transactional(readOnly = true)
    public Page<TrainingDto> search(Long requestedCompanyId, String keyword, Long programId, LocalDate from,
                                    LocalDate to, EStatus status, Pageable pageable) {
        Page<EmployeeTraining> page = trainings.search(TrainingSupport.company(requestedCompanyId), programId, from,
                to, status, DepartmentService.kw(keyword), pageable);
        Lookups l = lookups(page.getContent());
        return page.map(t -> dto(t, l));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long programId, LocalDate from,
                                     LocalDate to) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : trainings.countByStatus(TrainingSupport.company(requestedCompanyId), programId, from, to,
                DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long requestedCompanyId) {
        Long companyId = TrainingSupport.company(requestedCompanyId);
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        Map<Long, long[]> perProgram = new HashMap<>();
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : trainings.countByProgramOfCompany(companyId)) {
            EStatus s = (EStatus) r[1];
            long n = (Long) r[2];
            by.merge(s, n, Long::sum);
            long[] c = perProgram.computeIfAbsent((Long) r[0], k -> new long[2]);
            c[1] += n;
            if (s == EStatus.COMPLETED) {
                c[0] += n;
            }
        }
        long total = by.values().stream().mapToLong(Long::longValue).sum();
        Map<Long, String> names = programRepo.findAllById(perProgram.keySet()).stream()
                .collect(Collectors.toMap(TrainingProgram::getId, TrainingProgram::getName));
        List<ProgramCompletion> completion = perProgram.entrySet().stream()
                .map(en -> new ProgramCompletion(new Ref(en.getKey().toString(), names.get(en.getKey())),
                        en.getValue()[0], en.getValue()[1], rate(en.getValue()[0], en.getValue()[1])))
                .sorted(Comparator.comparing((ProgramCompletion p) -> p.program().name(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<EmployeeTraining> recent = trainings
                .findTop5ByCompanyIdAndStatusAndDeletedFalseOrderByCompletionDateDescIdDesc(companyId,
                        EStatus.COMPLETED);
        List<EmployeeTraining> upcoming = trainings
                .findTop5ByCompanyIdAndStatusAndAssignedDateGreaterThanEqualAndDeletedFalseOrderByAssignedDateAscIdAsc(
                        companyId, EStatus.ASSIGNED, LocalDate.now(clock.withZone(ZONE)));
        List<EmployeeTraining> both = new ArrayList<>(recent);
        both.addAll(upcoming);
        Lookups l = lookups(both);
        long completed = by.get(EStatus.COMPLETED);
        return new Dashboard(total, completed, rate(completed, total), by.get(EStatus.IN_PROGRESS),
                by.get(EStatus.ASSIGNED), by.get(EStatus.FAILED), completion,
                recent.stream().map(t -> dto(t, l)).toList(), upcoming.stream().map(t -> dto(t, l)).toList());
    }

    static int rate(long part, long total) {
        return total == 0 ? 0 : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(total), 0,
                RoundingMode.HALF_UP).intValue();
    }

    @Transactional(readOnly = true)
    public TrainingDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public TrainingDto assign(TrainingForm form) {
        EmployeeTraining t = new EmployeeTraining();
        t.setAssignedById(myEmployeeId());
        apply(t, form);
        return detail(trainings.save(t));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public TrainingDto update(Long id, TrainingForm form) {
        EmployeeTraining t = require(id);
        apply(t, form);
        return detail(t);
    }

    /** Bulk Assign: ASSIGNED for each employee; ones already assigned to the program are skipped. */
    @Transactional
    public BulkResult bulkAssign(BulkForm f) {
        List<Employee> people = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(f.employeeIds())) {
            Employee e = support.employee(id);
            EmployeeExit.assertEmployed(e);
            people.add(e);
        }
        Long companyId = people.get(0).getCompanyId();
        if (people.stream().anyMatch(e -> !e.getCompanyId().equals(companyId))) {
            throw ApiException.notFound(EEmployeeError.EMP_001);
        }
        TrainingProgram p = openProgram(f.programId(), companyId);
        Set<Long> already = trainings.findByProgramIdAndEmployeeIdInAndDeletedFalse(p.getId(), f.employeeIds())
                .stream().map(EmployeeTraining::getEmployeeId).collect(Collectors.toSet());
        List<Employee> fresh = people.stream().filter(e -> !already.contains(e.getId())).toList();
        if (p.getCapacity() != null && trainings.seatsTaken(p.getId(), -1L) + fresh.size() > p.getCapacity()) {
            throw ApiException.badRequest(EEmployeeError.EMP_084, p.getCapacity());
        }
        Long by = myEmployeeId();
        for (Employee e : fresh) {
            EmployeeTraining t = new EmployeeTraining();
            t.setCompanyId(companyId);
            t.setEmployeeId(e.getId());
            t.setProgramId(p.getId());
            t.setAssignedDate(f.assignedDate());
            t.setAssignedById(by);
            t.setNotes(TrainingSupport.blankToNull(f.notes()));
            trainings.save(t);
        }
        return new BulkResult(fresh.size(), already.stream().sorted().map(String::valueOf).toList());
    }

    /** A training with assessment results cannot be deleted (EMP_010). */
    @Transactional
    public void delete(Long id) {
        EmployeeTraining t = require(id);
        if (results.existsByEmployeeTrainingId(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        t.setDeleted(true);
    }

    private void apply(EmployeeTraining t, TrainingForm f) {
        Employee e = support.employee(f.employeeId());
        boolean newEmployee = t.getId() == null || !e.getId().equals(t.getEmployeeId());
        boolean newProgram = t.getId() == null || !f.programId().equals(t.getProgramId());
        if (newEmployee) {
            EmployeeExit.assertEmployed(e);
        }
        TrainingProgram p = newProgram ? openProgram(f.programId(), e.getCompanyId())
                : programs.usable(f.programId(), e.getCompanyId());
        Long selfId = t.getId() == null ? -1L : t.getId();
        if ((newEmployee || newProgram)
                && trainings.existsByProgramIdAndEmployeeIdAndDeletedFalseAndIdNot(p.getId(), e.getId(), selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_085);
        }
        EStatus status = f.status() != null ? f.status() : t.getStatus();
        if (p.getCapacity() != null && status != EStatus.FAILED
                && (newProgram || t.getStatus() == EStatus.FAILED)
                && trainings.seatsTaken(p.getId(), selfId) >= p.getCapacity()) {
            throw ApiException.badRequest(EEmployeeError.EMP_084, p.getCapacity());
        }
        LocalDate completion = f.completionDate();
        if (status == EStatus.COMPLETED && completion == null) {
            completion = LocalDate.now(clock.withZone(ZONE));
        }
        if (completion != null && completion.isBefore(f.assignedDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "completion date", "assigned date");
        }
        t.setCompanyId(e.getCompanyId());
        t.setEmployeeId(e.getId());
        t.setProgramId(p.getId());
        t.setStatus(status);
        t.setAssignedDate(f.assignedDate());
        t.setCompletionDate(completion);
        t.setCertificateMediaId(support.file(f.certificateMediaId(), e.getCompanyId()));
        t.setScore(f.score());
        t.setPassed(f.passed());
        t.setFeedback(TrainingSupport.blankToNull(f.feedback()));
        t.setNotes(TrainingSupport.blankToNull(f.notes()));
    }

    private TrainingProgram openProgram(Long programId, Long companyId) {
        TrainingProgram p = programs.usable(programId, companyId);
        if (!OPEN.contains(p.getStatus())) {
            throw ApiException.badRequest(EEmployeeError.EMP_083);
        }
        return p;
    }

    private Long myEmployeeId() {
        ErpPrincipal p = CurrentUser.find().orElse(null);
        if (p == null || p.companyId() == null || p.userId() == null) {
            return null;
        }
        return employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .map(Employee::getId).orElse(null);
    }

    private record Lookups(Map<Long, EmployeeRef> people, Map<Long, TrainingProgram> programs,
                           Map<Long, String> types) {
    }

    private Lookups lookups(List<EmployeeTraining> list) {
        Set<Long> people = new HashSet<>();
        Set<Long> programIds = new HashSet<>();
        for (EmployeeTraining t : list) {
            people.add(t.getEmployeeId());
            if (t.getAssignedById() != null) {
                people.add(t.getAssignedById());
            }
            programIds.add(t.getProgramId());
        }
        Map<Long, TrainingProgram> ps = programRepo.findAllById(programIds).stream()
                .collect(Collectors.toMap(TrainingProgram::getId, p -> p));
        Map<Long, String> types = typeRepo.findAllById(ps.values().stream().map(TrainingProgram::getTrainingTypeId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(TrainingType::getId,
                TrainingType::getName));
        return new Lookups(support.refs(people), ps, types);
    }

    private TrainingDto detail(EmployeeTraining t) {
        Lookups l = lookups(List.of(t));
        Employee e = employees.findById(t.getEmployeeId()).orElse(null);
        EmployeeDetails details = e == null ? null : new EmployeeDetails(e.getId().toString(), e.getEmployeeNo(),
                e.getFullName(), e.getEmail(), e.getPhone(),
                e.getDepartmentId() == null ? null : departments.findById(e.getDepartmentId())
                        .map(d -> d.getName()).orElse(null),
                e.getDesignationId() == null ? null : designations.findById(e.getDesignationId())
                        .map(d -> d.getName()).orElse(null),
                l.people().get(e.getId()) == null ? null : l.people().get(e.getId()).profileImageUrl());
        TrainingDto base = dto(t, l);
        return new TrainingDto(base.id(), base.employee(), base.program(), base.status(), base.assignedDate(),
                base.completionDate(), base.assignedBy(), base.score(), base.passed(), base.result(), base.feedback(),
                base.notes(), base.certificateId(), support.fileRef(t.getCertificateMediaId()), details,
                programs.detail(programs.require(t.getProgramId())), assessments.resultsOf(t), base.createdDate(),
                base.updatedDate());
    }

    private static TrainingDto dto(EmployeeTraining t, Lookups l) {
        TrainingProgram p = l.programs().get(t.getProgramId());
        ProgramRef program = new ProgramRef(t.getProgramId().toString(), p == null ? null : p.getName(),
                p == null ? null : l.types().get(p.getTrainingTypeId()));
        EResult result = t.getPassed() == null ? null : t.getPassed() ? EResult.PASSED : EResult.FAILED;
        return new TrainingDto(t.getId().toString(), l.people().get(t.getEmployeeId()), program, t.getStatus(),
                t.getAssignedDate(), t.getCompletionDate(),
                t.getAssignedById() == null ? null : l.people().get(t.getAssignedById()), t.getScore(),
                t.getPassed(), result, t.getFeedback(), t.getNotes(),
                t.getCertificateMediaId() == null ? null : t.getCertificateMediaId().toString(), null, null, null,
                null, t.getCreatedDate(), t.getUpdatedDate());
    }

    private EmployeeTraining require(Long id) {
        EmployeeTraining t = trainings.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_082));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_082);
        return t;
    }
}
