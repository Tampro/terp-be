package com.terp.erp.employee.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.training.TrainingAssessment.EType;
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Training Assessments page (docs/analysis/06 §7.4.4) and their results. A result belongs to an employee training of
 * the assessment's program (EMP_090), one per training (EMP_091); passed = score ≥ the passing score, so changing the
 * passing score re-grades every result (PO, 2026-11-20). An assessment with results cannot be deleted (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class TrainingAssessmentService {
    private final TrainingAssessmentRepository assessments;
    private final AssessmentResultRepository results;
    private final EmployeeTrainingRepository trainings;
    private final TrainingProgramRepository programRepo;
    private final TrainingProgramService programs;
    private final LifecycleSupport support;

    public record ResultDto(String id, String employeeTrainingId, EmployeeRef employee, BigDecimal score,
                            boolean passed, LocalDate assessedDate, String notes, Instant createdDate) {
    }

    /** passRate = passed / total %; averageScore 1 decimal, null without results. */
    public record Statistics(long totalResults, int passRate, BigDecimal averageScore, long passed, long failed) {
    }

    /** statistics and results only on the details. */
    public record AssessmentDto(String id, Ref program, String name, String description, EType type,
                                BigDecimal passingScore, String criteria, long results, String createdBy,
                                Statistics statistics, List<ResultDto> resultList, Instant createdDate,
                                Instant updatedDate) {
    }

    public record TypeCounts(long all, Map<EType, Long> byType) {
    }

    public record AssessmentForm(
            @Schema(description = "Entry of /training/programs", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long programId,
            @Schema(example = "Final quiz", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String name,
            @Size(max = 5000) String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EType type,
            @Schema(description = "%", example = "70", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal passingScore,
            @Size(max = 5000) String criteria) {
    }

    public record ResultForm(
            @Schema(description = "Employee training of the assessment's program", example = "9",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeTrainingId,
            @Schema(description = "%", example = "82.5", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score,
            @Schema(example = "2026-12-10") LocalDate assessedDate,
            @Size(max = 5000) String notes) {
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDto> search(Long requestedCompanyId, String keyword, Long programId, EType type,
                                      Pageable pageable) {
        Page<TrainingAssessment> page = assessments.search(TrainingSupport.company(requestedCompanyId), programId,
                type, DepartmentService.kw(keyword), pageable);
        Map<Long, String> names = programRepo.findAllById(page.map(TrainingAssessment::getProgramId).toSet())
                .stream().collect(Collectors.toMap(TrainingProgram::getId, TrainingProgram::getName));
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] r : results.countByAssessment(page.map(TrainingAssessment::getId).toList())) {
                counts.put((Long) r[0], (Long) r[1]);
            }
        }
        return page.map(a -> dto(a, names.get(a.getProgramId()), counts.getOrDefault(a.getId(), 0L), null, null));
    }

    /** The type tabs (All, Quiz, Practical, Presentation). */
    @Transactional(readOnly = true)
    public TypeCounts typeCounts(Long requestedCompanyId, String keyword, Long programId) {
        Map<EType, Long> by = new EnumMap<>(EType.class);
        for (EType t : EType.values()) {
            by.put(t, 0L);
        }
        for (Object[] r : assessments.countByType(TrainingSupport.company(requestedCompanyId), programId,
                DepartmentService.kw(keyword))) {
            by.put((EType) r[0], (Long) r[1]);
        }
        return new TypeCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public AssessmentDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public AssessmentDto create(AssessmentForm form) {
        TrainingAssessment a = new TrainingAssessment();
        apply(a, form);
        return detail(assessments.save(a));
    }

    /** Full replacement; the program cannot change once results exist (they belong to its trainings). */
    @Transactional
    public AssessmentDto update(Long id, AssessmentForm form) {
        TrainingAssessment a = require(id);
        if (!form.programId().equals(a.getProgramId()) && results.existsByAssessmentId(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        apply(a, form);
        return detail(a);
    }

    @Transactional
    public void delete(Long id) {
        TrainingAssessment a = require(id);
        if (results.existsByAssessmentId(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        a.setDeleted(true);
    }

    @Transactional
    public AssessmentDto addResult(Long id, ResultForm f) {
        TrainingAssessment a = require(id);
        AssessmentResult r = new AssessmentResult();
        r.setAssessmentId(a.getId());
        applyResult(a, r, f, -1L);
        results.save(r);
        return detail(a);
    }

    /** Full replacement of the result. */
    @Transactional
    public AssessmentDto updateResult(Long id, Long resultId, ResultForm f) {
        TrainingAssessment a = require(id);
        AssessmentResult r = requireResult(a, resultId);
        applyResult(a, r, f, r.getId());
        return detail(a);
    }

    @Transactional
    public AssessmentDto deleteResult(Long id, Long resultId) {
        TrainingAssessment a = require(id);
        results.delete(requireResult(a, resultId));
        results.flush();
        return detail(a);
    }

    /** The Assessment Results tab of an employee training. */
    @Transactional(readOnly = true)
    public List<EmployeeTrainingResult> resultsOf(EmployeeTraining t) {
        List<AssessmentResult> list = results.findByEmployeeTrainingIdOrderByIdAsc(t.getId());
        Map<Long, TrainingAssessment> byId = assessments.findAllById(list.stream()
                        .map(AssessmentResult::getAssessmentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(TrainingAssessment::getId, Function.identity()));
        return list.stream().filter(r -> byId.containsKey(r.getAssessmentId())).map(r -> {
            TrainingAssessment a = byId.get(r.getAssessmentId());
            return new EmployeeTrainingResult(r.getId().toString(), new Ref(a.getId().toString(), a.getName()),
                    a.getType(), a.getPassingScore(), r.getScore(), passed(r, a), r.getAssessedDate(), r.getNotes());
        }).toList();
    }

    public record EmployeeTrainingResult(String id, Ref assessment, EType type, BigDecimal passingScore,
                                         BigDecimal score, boolean passed, LocalDate assessedDate, String notes) {
    }

    private void applyResult(TrainingAssessment a, AssessmentResult r, ResultForm f, Long selfId) {
        EmployeeTraining t = trainings.findByIdAndDeletedFalse(f.employeeTrainingId())
                .filter(x -> x.getProgramId().equals(a.getProgramId()))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_090));
        if (results.existsByAssessmentIdAndEmployeeTrainingIdAndIdNot(a.getId(), t.getId(), selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_091);
        }
        r.setEmployeeTrainingId(t.getId());
        r.setScore(f.score());
        r.setAssessedDate(f.assessedDate());
        r.setNotes(TrainingSupport.blankToNull(f.notes()));
    }

    static boolean passed(AssessmentResult r, TrainingAssessment a) {
        return r.getScore().compareTo(a.getPassingScore()) >= 0;
    }

    static Statistics statistics(List<AssessmentResult> list, TrainingAssessment a) {
        long passed = list.stream().filter(r -> passed(r, a)).count();
        BigDecimal avg = list.isEmpty() ? null : list.stream().map(AssessmentResult::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
        return new Statistics(list.size(), EmployeeTrainingService.rate(passed, list.size()), avg, passed,
                list.size() - passed);
    }

    private void apply(TrainingAssessment a, AssessmentForm f) {
        // A new assessment takes the program's company (a super admin names no company here).
        TrainingProgram p = a.getCompanyId() == null ? programs.require(f.programId())
                : programs.usable(f.programId(), a.getCompanyId());
        a.setCompanyId(p.getCompanyId());
        a.setProgramId(p.getId());
        a.setName(f.name().trim());
        a.setDescription(TrainingSupport.blankToNull(f.description()));
        a.setType(f.type());
        a.setPassingScore(f.passingScore());
        a.setCriteria(TrainingSupport.blankToNull(f.criteria()));
    }

    private AssessmentDto detail(TrainingAssessment a) {
        List<AssessmentResult> list = results.findByAssessmentIdOrderByScoreDescIdAsc(a.getId());
        Map<Long, EmployeeTraining> ts = trainings.findAllById(list.stream()
                        .map(AssessmentResult::getEmployeeTrainingId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(EmployeeTraining::getId, Function.identity()));
        Map<Long, EmployeeRef> people = support.refs(ts.values().stream().map(EmployeeTraining::getEmployeeId)
                .collect(Collectors.toSet()));
        List<ResultDto> rows = list.stream().map(r -> {
            EmployeeTraining t = ts.get(r.getEmployeeTrainingId());
            return new ResultDto(r.getId().toString(), r.getEmployeeTrainingId().toString(),
                    t == null ? null : people.get(t.getEmployeeId()), r.getScore(), passed(r, a),
                    r.getAssessedDate(), r.getNotes(), r.getCreatedDate());
        }).toList();
        String program = programRepo.findById(a.getProgramId()).map(TrainingProgram::getName).orElse(null);
        return dto(a, program, list.size(), statistics(list, a), rows);
    }

    private static AssessmentDto dto(TrainingAssessment a, String program, long count, Statistics stats,
                                     List<ResultDto> rows) {
        return new AssessmentDto(a.getId().toString(), new Ref(a.getProgramId().toString(), program), a.getName(),
                a.getDescription(), a.getType(), a.getPassingScore(), a.getCriteria(), count, a.getCreatedBy(),
                stats, rows, a.getCreatedDate(), a.getUpdatedDate());
    }

    private AssessmentResult requireResult(TrainingAssessment a, Long resultId) {
        return results.findByIdAndAssessmentId(resultId, a.getId())
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_092));
    }

    private TrainingAssessment require(Long id) {
        TrainingAssessment a = assessments.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_089));
        TenantGuard.assertSameCompany(a.getCompanyId(), EEmployeeError.EMP_089);
        return a;
    }
}
