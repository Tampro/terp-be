package com.terp.erp.employee.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trainers page (FR-TRN-002, manual 16). Branch of the company (EMP_002); the department belongs to that branch
 * (EMP_003). A trainer named on a session cannot be deleted (EMP_010); deactivate it instead.
 */
@Service
@RequiredArgsConstructor
public class TrainerService {
    private final TrainerRepository trainers;
    private final TrainingSessionRepository sessions;
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final OutboxPublisher outbox;

    public record TrainerDto(String id, String name, String contactPhone, String email, Integer experienceYears,
                             Ref branch, Ref department, String expertise, String qualification, boolean active,
                             Instant createdDate, Instant updatedDate) {
    }

    public record TrainerForm(
            @Schema(example = "Jane Trainer", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Schema(description = "Contact phone", example = "0900000000") @Size(max = 30) String contactPhone,
            @Schema(example = "trainer@example.invalid") @Email @Size(max = 150) String email,
            @Schema(description = "Years of experience, 0–80", example = "5") @Min(0) @Max(80) Integer experienceYears,
            @Schema(example = "1") Long branchId,
            @Schema(description = "A department of the branch; needs branchId", example = "2") Long departmentId,
            @Schema(example = "Leadership, sales") @Size(max = 500) String expertise,
            @Schema(example = "MBA") @Size(max = 500) String qualification,
            @Schema(description = "Omitted = true") Boolean active) {
    }

    @Transactional(readOnly = true)
    public Page<TrainerDto> search(Long requestedCompanyId, String keyword, Long branchId, Long departmentId,
                                   Boolean active, Pageable pageable) {
        Page<Trainer> page = trainers.search(TrainingSupport.company(requestedCompanyId), branchId, departmentId,
                active, DepartmentService.kw(keyword), pageable);
        Names n = names(page.getContent());
        return page.map(t -> dto(t, n));
    }

    /** Active trainers for the session dropdown. */
    @Transactional(readOnly = true)
    public List<Ref> options(Long requestedCompanyId) {
        return trainers.findByCompanyIdAndActiveTrueAndDeletedFalseOrderByNameAsc(
                        TrainingSupport.company(requestedCompanyId)).stream()
                .map(t -> new Ref(t.getId().toString(), t.getName())).toList();
    }

    @Transactional(readOnly = true)
    public TrainerDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public TrainerDto create(Long requestedCompanyId, TrainerForm form) {
        Trainer t = new Trainer();
        t.setCompanyId(TrainingSupport.company(requestedCompanyId));
        apply(t, form);
        Trainer saved = trainers.save(t);
        // webhook NEW_TRAINER; no recipient (contact phone/email of the trainer are not sent)
        outbox.publish(saved.getCompanyId(), "employee.trainer.created", "TRAINER", saved.getId(), Payload.of(
                "trainerId", saved.getId(), "title", saved.getName(), "name", saved.getName(),
                "expertise", saved.getExpertise(), "branchId", saved.getBranchId(),
                "departmentId", saved.getDepartmentId()));
        return detail(saved);
    }

    /** Full replacement; active omitted = true. */
    @Transactional
    public TrainerDto update(Long id, TrainerForm form) {
        Trainer t = require(id);
        apply(t, form);
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        Trainer t = require(id);
        if (sessions.existsByTrainerIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        t.setDeleted(true);
    }

    /**
     * An active trainer of the company for a session; any other id reads as not in the list (EMP_101). keep = the
     * session's current trainer, accepted even if deactivated since.
     */
    Trainer usable(Long id, Long companyId, Long keep) {
        return trainers.findByIdAndDeletedFalse(id)
                .filter(t -> t.getCompanyId().equals(companyId) && (t.isActive() || id.equals(keep)))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_101));
    }

    private void apply(Trainer t, TrainerForm f) {
        if (f.branchId() != null) {
            branches.findByIdAndDeletedFalse(f.branchId()).filter(b -> b.getCompanyId().equals(t.getCompanyId()))
                    .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_002));
        }
        if (f.departmentId() != null) {
            departments.findByIdAndDeletedFalse(f.departmentId())
                    .filter(d -> d.getCompanyId().equals(t.getCompanyId()) && f.branchId() != null
                            && Objects.equals(d.getBranchId(), f.branchId()))
                    .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_003));
        }
        t.setName(f.name().trim());
        t.setContactPhone(TrainingSupport.blankToNull(f.contactPhone()));
        t.setEmail(TrainingSupport.blankToNull(f.email()));
        t.setExperienceYears(f.experienceYears());
        t.setBranchId(f.branchId());
        t.setDepartmentId(f.departmentId());
        t.setExpertise(TrainingSupport.blankToNull(f.expertise()));
        t.setQualification(TrainingSupport.blankToNull(f.qualification()));
        t.setActive(f.active() == null || f.active());
    }

    private record Names(Map<Long, String> branches, Map<Long, String> departments) {
    }

    private Names names(List<Trainer> list) {
        Set<Long> b = new HashSet<>();
        Set<Long> d = new HashSet<>();
        for (Trainer t : list) {
            if (t.getBranchId() != null) {
                b.add(t.getBranchId());
            }
            if (t.getDepartmentId() != null) {
                d.add(t.getDepartmentId());
            }
        }
        return new Names(b.isEmpty() ? Map.of() : branches.findAllById(b).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName)),
                d.isEmpty() ? Map.of() : departments.findAllById(d).stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName)));
    }

    private TrainerDto detail(Trainer t) {
        return dto(t, names(List.of(t)));
    }

    private static TrainerDto dto(Trainer t, Names n) {
        return new TrainerDto(t.getId().toString(), t.getName(), t.getContactPhone(), t.getEmail(),
                t.getExperienceYears(), ref(t.getBranchId(), n.branches()), ref(t.getDepartmentId(), n.departments()),
                t.getExpertise(), t.getQualification(), t.isActive(), t.getCreatedDate(), t.getUpdatedDate());
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id.toString(), names.get(id));
    }

    private Trainer require(Long id) {
        Trainer t = trainers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_100));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_100);
        return t;
    }
}
