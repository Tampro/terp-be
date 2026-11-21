package com.terp.erp.employee.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
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
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.training.TrainingProgram.EStatus;
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Training Programs page (docs/analysis/06 §7.4.3). The type is one of the company's training types (EMP_080); names
 * are unique per company (EMP_011); materials are media library files of the company (EMP_012).
 */
@Service
@RequiredArgsConstructor
public class TrainingProgramService {
    private final TrainingProgramRepository programs;
    private final TrainingTypeRepository typeRepo;
    private final TrainingTypeService types;
    private final LifecycleSupport support;
    private final EmployeeTrainingRepository trainings;
    private final TrainingSessionRepository sessions;
    private final TrainingAssessmentRepository assessments;

    /**
     * employees = assigned employee trainings; completedTrainings = of them COMPLETED; sessions / completedSessions
     * likewise for the sessions (rates on the cards = completed / total). materials only on the details.
     */
    public record ProgramDto(String id, String name, Ref trainingType, String description, BigDecimal durationHours,
                             BigDecimal cost, Integer capacity, EStatus status, String prerequisites,
                             boolean mandatory, boolean selfEnrollment, List<String> materialIds,
                             List<FileRef> materials, long employees, long completedTrainings,
                             long sessions, long completedSessions,
                             Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record ProgramForm(
            @Schema(example = "Customer Service Basics", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String name,
            @Schema(description = "Entry of /training/types", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long trainingTypeId,
            @Size(max = 5000) String description,
            @Schema(example = "16") @DecimalMin("0") @Digits(integer = 6, fraction = 1) BigDecimal durationHours,
            @Schema(description = "Company currency", example = "2000000") @DecimalMin("0")
            @Digits(integer = 13, fraction = 2) BigDecimal cost,
            @Schema(description = "Maximum participants; omitted = no limit", example = "20") @Min(1)
            Integer capacity,
            @Schema(description = "Omitted = DRAFT on create, kept on edit") EStatus status,
            @Schema(description = "Media library file ids; full set") List<@NotNull Long> materialIds,
            @Size(max = 5000) String prerequisites,
            boolean mandatory,
            boolean selfEnrollment) {
    }

    @Transactional(readOnly = true)
    public Page<ProgramDto> search(Long requestedCompanyId, String keyword, Long typeId, EStatus status,
                                   Pageable pageable) {
        Page<TrainingProgram> page = programs.search(TrainingSupport.company(requestedCompanyId), typeId, status,
                DepartmentService.kw(keyword), pageable);
        Map<Long, String> typeNames = typeNames(page.map(TrainingProgram::getTrainingTypeId).toSet());
        Map<Long, long[]> stats = stats(page.map(TrainingProgram::getId).toList());
        return page.map(p -> dto(p, typeNames, stats, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long typeId) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : programs.countByStatus(TrainingSupport.company(requestedCompanyId), typeId,
                DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Draft and active programs, for the session / assignment dropdowns. */
    @Transactional(readOnly = true)
    public List<Ref> options(Long requestedCompanyId) {
        return programs.findByCompanyIdAndStatusInAndDeletedFalseOrderByNameAsc(
                        TrainingSupport.company(requestedCompanyId), EnumSet.of(EStatus.DRAFT, EStatus.ACTIVE))
                .stream().map(p -> new Ref(p.getId().toString(), p.getName())).toList();
    }

    @Transactional(readOnly = true)
    public ProgramDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public ProgramDto create(Long requestedCompanyId, ProgramForm form) {
        TrainingProgram p = new TrainingProgram();
        p.setCompanyId(TrainingSupport.company(requestedCompanyId));
        apply(p, form, -1L);
        return detail(programs.save(p));
    }

    /** Full replacement; status omitted = kept; materialIds omitted = none. */
    @Transactional
    public ProgramDto update(Long id, ProgramForm form) {
        TrainingProgram p = require(id);
        apply(p, form, id);
        return detail(p);
    }

    /** A program with employee trainings, sessions or assessments cannot be deleted (EMP_010). */
    @Transactional
    public void delete(Long id) {
        TrainingProgram p = require(id);
        if (trainings.existsByProgramIdAndDeletedFalse(id) || sessions.existsByProgramIdAndDeletedFalse(id)
                || assessments.existsByProgramIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        p.setDeleted(true);
    }

    /** A program of the company; any other id reads as not found. */
    public TrainingProgram usable(Long id, Long companyId) {
        return programs.findByIdAndDeletedFalse(id).filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_081));
    }

    private void apply(TrainingProgram p, ProgramForm f, Long selfId) {
        String name = f.name().trim();
        if (programs.nameTaken(p.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        p.setTrainingTypeId(types.usable(f.trainingTypeId(), p.getCompanyId()).getId());
        Set<Long> materials = new LinkedHashSet<>();
        for (Long m : f.materialIds() == null ? List.<Long>of() : f.materialIds()) {
            materials.add(support.file(m, p.getCompanyId()));
        }
        p.setName(name);
        p.setDescription(TrainingSupport.blankToNull(f.description()));
        p.setDurationHours(f.durationHours());
        p.setCost(f.cost());
        p.setCapacity(f.capacity());
        if (f.status() != null) {
            p.setStatus(f.status());
        }
        p.setPrerequisites(TrainingSupport.blankToNull(f.prerequisites()));
        p.setMandatory(f.mandatory());
        p.setSelfEnrollment(f.selfEnrollment());
        p.getMaterialIds().clear();
        p.getMaterialIds().addAll(materials);
    }

    private Map<Long, String> typeNames(Set<Long> ids) {
        return typeRepo.findAllById(ids).stream().collect(Collectors.toMap(TrainingType::getId, TrainingType::getName));
    }

    ProgramDto detail(TrainingProgram p) {
        return dto(p, typeNames(Set.of(p.getTrainingTypeId())), stats(List.of(p.getId())), true);
    }

    /** programId -> [employees, completed trainings, sessions, completed sessions]. */
    private Map<Long, long[]> stats(List<Long> programIds) {
        Map<Long, long[]> out = new java.util.HashMap<>();
        if (programIds.isEmpty()) {
            return out;
        }
        for (Object[] r : trainings.countByProgram(programIds)) {
            long[] c = out.computeIfAbsent((Long) r[0], k -> new long[4]);
            c[0] += (Long) r[2];
            if (r[1] == EmployeeTraining.EStatus.COMPLETED) {
                c[1] += (Long) r[2];
            }
        }
        for (Object[] r : sessions.countByProgram(programIds)) {
            long[] c = out.computeIfAbsent((Long) r[0], k -> new long[4]);
            c[2] += (Long) r[2];
            if (r[1] == TrainingSession.EStatus.COMPLETED) {
                c[3] += (Long) r[2];
            }
        }
        return out;
    }

    private ProgramDto dto(TrainingProgram p, Map<Long, String> typeNames, Map<Long, long[]> stats,
                           boolean withFiles) {
        long[] c = stats.getOrDefault(p.getId(), new long[4]);
        List<Long> ids = p.getMaterialIds().stream().sorted().toList();
        return new ProgramDto(p.getId().toString(), p.getName(), new Ref(p.getTrainingTypeId().toString(),
                typeNames.get(p.getTrainingTypeId())), p.getDescription(), p.getDurationHours(), p.getCost(),
                p.getCapacity(), p.getStatus(), p.getPrerequisites(), p.isMandatory(), p.isSelfEnrollment(),
                ids.stream().map(String::valueOf).toList(),
                withFiles ? ids.stream().map(support::fileRef).filter(Objects::nonNull).toList() : null,
                c[0], c[1], c[2], c[3], p.getCreatedDate(), p.getUpdatedDate());
    }

    TrainingProgram require(Long id) {
        TrainingProgram p = programs.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_081));
        TenantGuard.assertSameCompany(p.getCompanyId(), EEmployeeError.EMP_081);
        return p;
    }
}
