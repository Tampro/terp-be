package com.terp.erp.employee.training;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Training Types page (docs/analysis/06 §7.4.5). Optional branch; Assign Departments replaces the departments, which
 * must be of the company and of the branch when one is set (EMP_003). A type with programs cannot be deleted
 * (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class TrainingTypeService {
    private final TrainingTypeRepository types;
    private final TrainingProgramRepository programs;
    private final BranchRepository branches;
    private final DepartmentRepository departments;

    /** departments carry their branch so the list can show "department (branch)". */
    public record DepartmentRef(String id, String name, Ref branch) {
    }

    public record TypeDto(String id, String name, String description, Ref branch, List<DepartmentRef> departments,
                          long programs, Instant createdDate, Instant updatedDate) {
    }

    public record TypeForm(
            @Schema(example = "Customer Service Excellence", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Schema(description = "Applicable branch; omitted = every branch", example = "1") Long branchId) {
    }

    public record DepartmentsForm(
            @Schema(description = "Full set; replaces the current departments", example = "[\"2\",\"3\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Set<@NotNull Long> departmentIds) {
    }

    @Transactional(readOnly = true)
    public Page<TypeDto> search(Long requestedCompanyId, String keyword, Long branchId, Long departmentId,
                                Pageable pageable) {
        Page<TrainingType> page = types.search(TrainingSupport.company(requestedCompanyId), branchId, departmentId,
                DepartmentService.kw(keyword), pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] r : programs.countByType(page.map(TrainingType::getId).toList())) {
                counts.put((Long) r[0], (Long) r[1]);
            }
        }
        Names names = names(page.getContent());
        return page.map(t -> dto(t, names, counts.getOrDefault(t.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public List<Ref> options(Long requestedCompanyId) {
        return types.findByCompanyIdAndDeletedFalseOrderByNameAsc(TrainingSupport.company(requestedCompanyId))
                .stream().map(t -> new Ref(t.getId().toString(), t.getName())).toList();
    }

    @Transactional(readOnly = true)
    public TypeDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public TypeDto create(Long requestedCompanyId, TypeForm form) {
        TrainingType t = new TrainingType();
        t.setCompanyId(TrainingSupport.company(requestedCompanyId));
        apply(t, form, -1L);
        return detail(types.save(t));
    }

    /** Full replacement of name, description and branch; departments are kept (and must fit a new branch). */
    @Transactional
    public TypeDto update(Long id, TypeForm form) {
        TrainingType t = require(id);
        apply(t, form, id);
        return detail(t);
    }

    @Transactional
    public TypeDto assignDepartments(Long id, Set<Long> departmentIds) {
        TrainingType t = require(id);
        assertDepartments(t.getCompanyId(), t.getBranchId(), departmentIds);
        t.getDepartmentIds().clear();
        t.getDepartmentIds().addAll(departmentIds);
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        TrainingType t = require(id);
        if (programs.existsByTrainingTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        t.setDeleted(true);
    }

    /** A type of the company for a program; any other id reads as not in the list. */
    TrainingType usable(Long id, Long companyId) {
        return types.findByIdAndDeletedFalse(id).filter(t -> t.getCompanyId().equals(companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_080));
    }

    private void apply(TrainingType t, TypeForm f, Long selfId) {
        String name = f.name().trim();
        if (types.nameTaken(t.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        if (f.branchId() != null) {
            branches.findByIdAndDeletedFalse(f.branchId()).filter(b -> b.getCompanyId().equals(t.getCompanyId()))
                    .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_002));
        }
        assertDepartments(t.getCompanyId(), f.branchId(), t.getDepartmentIds());
        t.setName(name);
        t.setDescription(TrainingSupport.blankToNull(f.description()));
        t.setBranchId(f.branchId());
    }

    private void assertDepartments(Long companyId, Long branchId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<Department> found = departments.findByCompanyIdAndIdInAndDeletedFalse(companyId, ids);
        if (found.size() != ids.size()
                || (branchId != null && found.stream().anyMatch(d -> !Objects.equals(d.getBranchId(), branchId)))) {
            throw ApiException.badRequest(EEmployeeError.EMP_003);
        }
    }

    private record Names(Map<Long, String> branches, Map<Long, Department> departments) {
    }

    private Names names(List<TrainingType> list) {
        Set<Long> deptIds = new HashSet<>();
        Set<Long> branchIds = new HashSet<>();
        for (TrainingType t : list) {
            deptIds.addAll(t.getDepartmentIds());
            if (t.getBranchId() != null) {
                branchIds.add(t.getBranchId());
            }
        }
        Map<Long, Department> depts = deptIds.isEmpty() ? Map.of() : departments.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));
        depts.values().forEach(d -> branchIds.add(d.getBranchId()));
        Map<Long, String> branchNames = branchIds.isEmpty() ? Map.of() : branches.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
        return new Names(branchNames, depts);
    }

    private TypeDto detail(TrainingType t) {
        long count = 0;
        for (Object[] r : programs.countByType(List.of(t.getId()))) {
            count = (Long) r[1];
        }
        return dto(t, names(List.of(t)), count);
    }

    private static TypeDto dto(TrainingType t, Names n, long programCount) {
        List<DepartmentRef> depts = t.getDepartmentIds().stream().map(n.departments()::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Department::getName))
                .map(d -> new DepartmentRef(d.getId().toString(), d.getName(),
                        new Ref(d.getBranchId().toString(), n.branches().get(d.getBranchId())))).toList();
        return new TypeDto(t.getId().toString(), t.getName(), t.getDescription(),
                t.getBranchId() == null ? null : new Ref(t.getBranchId().toString(),
                        n.branches().get(t.getBranchId())), depts, programCount, t.getCreatedDate(),
                t.getUpdatedDate());
    }

    private TrainingType require(Long id) {
        TrainingType t = types.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_080));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_080);
        return t;
    }
}
