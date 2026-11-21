package com.terp.erp.employee.performance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
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
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.EmployeeExit;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.performance.EmployeeGoal.EStatus;
import com.terp.erp.employee.performance.PerformanceSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Employee Goals page (docs/analysis/06 §7.3.2). The goal type is an active goal type of the company; end ≥ start
 * (EMP_052); no new goal for a terminated employee (EMP_058).
 */
@Service
@RequiredArgsConstructor
public class GoalService {
    private final EmployeeGoalRepository goals;
    private final PerformanceCatalogService catalogs;
    private final PerformanceCatalogRepository catalogItems;
    private final LifecycleSupport support;

    /** days counts both ends, like the trip period. */
    public record GoalDto(String id, EmployeeRef employee, Ref goalType, String title, String description,
                          LocalDate startDate, LocalDate endDate, long days, String target, int progress,
                          EStatus status, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record GoalForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Entry of /performance/goal-types", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long goalTypeId,
            @Schema(example = "Ship the billing module", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2027-02-28", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(example = "Complete 5 projects") @Size(max = 200) String target,
            @Schema(description = "0–100", example = "0") @Min(0) @Max(100) int progress,
            @Schema(description = "Omitted = NOT_STARTED on create, kept on edit") EStatus status) {
    }

    public record ProgressForm(
            @Schema(description = "0–100", example = "60", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @Min(0) @Max(100) Integer progress) {
    }

    @Transactional(readOnly = true)
    public Page<GoalDto> search(Long requestedCompanyId, String keyword, Long employeeId, Long goalTypeId,
                                EStatus status, Pageable pageable) {
        Page<EmployeeGoal> page = goals.search(PerformanceSupport.company(requestedCompanyId), employeeId,
                goalTypeId, status, DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeGoal::getEmployeeId).toSet());
        Map<Long, String> types = names(page.map(EmployeeGoal::getGoalTypeId).toList());
        return page.map(g -> dto(g, people.get(g.getEmployeeId()), types));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId, Long goalTypeId) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : goals.countByStatus(PerformanceSupport.company(requestedCompanyId), employeeId, goalTypeId,
                DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public GoalDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public GoalDto create(GoalForm form) {
        EmployeeGoal g = new EmployeeGoal();
        apply(g, form);
        return detail(goals.save(g));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public GoalDto update(Long id, GoalForm form) {
        EmployeeGoal g = require(id);
        apply(g, form);
        return detail(g);
    }

    /**
     * Update Progress modal. TODO(assumption): progress moves the status along — above 0 starts a NOT_STARTED goal,
     * 100 completes it, below 100 reopens a COMPLETED one — confirm with BA.
     */
    @Transactional
    public GoalDto updateProgress(Long id, int progress) {
        EmployeeGoal g = require(id);
        g.setProgress(progress);
        if (progress == 100) {
            g.setStatus(EStatus.COMPLETED);
        } else if (progress > 0 && g.getStatus() != EStatus.IN_PROGRESS) {
            g.setStatus(EStatus.IN_PROGRESS);
        } else if (progress == 0 && g.getStatus() == EStatus.COMPLETED) {
            g.setStatus(EStatus.IN_PROGRESS);
        }
        return detail(g);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(EmployeeGoal g, GoalForm f) {
        Employee e = support.employee(f.employeeId());
        if (g.getId() == null || !e.getId().equals(g.getEmployeeId())) {
            EmployeeExit.assertEmployed(e);
        }
        if (f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "end date", "start date");
        }
        g.setGoalTypeId(catalogs.usable(EPerformanceCatalogKind.GOAL_TYPE, f.goalTypeId(), e.getCompanyId(),
                g.getGoalTypeId()).getId());
        g.setCompanyId(e.getCompanyId());
        g.setEmployeeId(e.getId());
        g.setTitle(f.title().trim());
        g.setDescription(PerformanceSupport.blankToNull(f.description()));
        g.setStartDate(f.startDate());
        g.setEndDate(f.endDate());
        g.setTarget(PerformanceSupport.blankToNull(f.target()));
        g.setProgress(f.progress());
        if (f.status() != null) {
            g.setStatus(f.status());
        }
    }

    private Map<Long, String> names(List<Long> ids) {
        return catalogItems.findAllById(ids).stream()
                .collect(Collectors.toMap(PerformanceCatalog::getId, PerformanceCatalog::getName));
    }

    private GoalDto detail(EmployeeGoal g) {
        return dto(g, support.refs(List.of(g.getEmployeeId())).get(g.getEmployeeId()),
                names(List.of(g.getGoalTypeId())));
    }

    private static GoalDto dto(EmployeeGoal g, EmployeeRef employee, Map<Long, String> types) {
        return new GoalDto(g.getId().toString(), employee, new Ref(g.getGoalTypeId().toString(),
                types.get(g.getGoalTypeId())), g.getTitle(), g.getDescription(), g.getStartDate(), g.getEndDate(),
                ChronoUnit.DAYS.between(g.getStartDate(), g.getEndDate()) + 1, g.getTarget(), g.getProgress(),
                g.getStatus(), g.getCreatedDate(), g.getUpdatedDate());
    }

    private EmployeeGoal require(Long id) {
        EmployeeGoal g = goals.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_074));
        TenantGuard.assertSameCompany(g.getCompanyId(), EEmployeeError.EMP_074);
        return g;
    }
}
