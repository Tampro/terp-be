package com.terp.erp.project.project;

import io.swagger.v3.oas.annotations.media.Schema;
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
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.client.AccountingClient;
import com.terp.erp.project.client.IamClient;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskRepository;
import com.terp.erp.security.OwnScope;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Projects (ERP manual 11.1, FR-PRJ-001/003). The form-builder conversion calls {@link #create(ProjectForm)}.
 * PROJECT_VIEW sees every project of the company; PROJECT_VIEW_OWN only those I am on the team of or created
 * (see {@link ProjectScope}).
 */
@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final StageService stages;
    private final IamClient iam;
    private final AccountingClient accounting;
    private final ActivityService activity;
    private final Clock clock;

    /** Also the body of PUT /projects/{id} (full replacement; companyId is then ignored). */
    public record ProjectForm(
            @Schema(description = "Super admin only: the company of the new project", example = "1")
            Long companyId,
            @Schema(description = "Name", example = "Website redesign", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 191) String name,
            @Schema(description = "Start date", example = "2026-12-01") LocalDate startDate,
            @Schema(description = "End date, not before the start date", example = "2027-03-31") LocalDate endDate,
            @Schema(description = "Budget", example = "150000000")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal budget,
            @Schema(description = "Description") @Size(max = 20000) String description,
            @Schema(description = "Team members: IAM user ids of the same company")
            List<@NotNull Long> userIds) {
    }

    public record StatusForm(
            @Schema(description = "ONGOING, ON_HOLD or FINISHED (card label Completed)", example = "ON_HOLD",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EProjectStatus status) {
    }

    public record ClientsForm(
            @Schema(description = "Every client (accounting customer id) of the project: full replacement",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull List<@NotNull Long> customerIds) {
    }

    public record ProjectDto(String id, String companyId, String name, LocalDate startDate, LocalDate endDate,
                             BigDecimal budget, String description, EProjectStatus status,
                             @Schema(description = "End date passed and not FINISHED") boolean overdue,
                             List<String> userIds, List<String> clientIds, Instant createdDate) {
    }

    public record ProjectDetailDto(ProjectDto project,
                                   @Schema(description = "Days from today to the end date; negative when late, null without end date")
                                   Long daysLeft,
                                   @Schema(description = "Percent of tasks in the done (last) stage, 0-100") int progress,
                                   long taskCount, long doneTaskCount) {
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public Page<ProjectDto> list(Long companyId, EProjectStatus status, String q, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        String term = q == null || q.isBlank() ? null : q.trim();
        Page<Project> page = ProjectScope.seesAll() ? projects.search(company, status, term, pageable)
                : projects.searchOwn(company, status, term, OwnScope.userId(), OwnScope.sub(), pageable);
        return page.map(this::dto);
    }

    /** Write transaction: progress needs the done stage, created on first use. */
    @Transactional
    public ProjectDetailDto detail(Long id) {
        Project p = require(id);
        long total = tasks.countByProjectIdAndDeletedFalse(p.getId());
        long done = tasks.countByProjectIdAndStageIdAndDeletedFalse(p.getId(), stages.doneStageId(p.getCompanyId()));
        return new ProjectDetailDto(dto(p), daysLeft(p), percent(done, total), total, done);
    }

    // ---------- writes ----------

    @Transactional
    public ProjectDto create(ProjectForm f) {
        Long company = TenantGuard.resolveCompany(f.companyId(), EProjectError.PRJ_001);
        checkDates(f.startDate(), f.endDate());
        Project p = new Project();
        p.setCompanyId(company);
        apply(p, f);
        p.setUserIds(checkedTeam(company, Set.of(), f.userIds()));
        projects.save(p);
        activity.log(company, p.getId(), "PROJECT_CREATED", p.getName());
        return dto(p);
    }

    /** Full replacement of the fields and the team; members removed leave the tasks they were assigned. */
    @Transactional
    public ProjectDto update(Long id, ProjectForm f) {
        Project p = requireManaged(id);
        checkDates(f.startDate(), f.endDate());
        Set<Long> team = checkedTeam(p.getCompanyId(), p.getUserIds(), f.userIds());
        apply(p, f);
        Set<Long> removed = new LinkedHashSet<>(p.getUserIds());
        removed.removeAll(team);
        p.getUserIds().clear();
        p.getUserIds().addAll(team);
        if (!removed.isEmpty()) {
            for (Task t : tasks.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(p.getId())) {
                t.getAssigneeIds().removeAll(removed);
            }
        }
        activity.log(p.getCompanyId(), p.getId(), "PROJECT_UPDATED", p.getName());
        return dto(p);
    }

    @Transactional
    public ProjectDto changeStatus(Long id, StatusForm f) {
        Project p = requireManaged(id);
        if (p.getStatus() != f.status()) {
            activity.log(p.getCompanyId(), p.getId(), "PROJECT_STATUS", p.getStatus() + " -> " + f.status());
            p.setStatus(f.status());
        }
        return dto(p);
    }

    @Transactional
    public ProjectDto replaceClients(Long id, ClientsForm f) {
        Project p = requireManaged(id);
        Set<Long> wanted = new LinkedHashSet<>(f.customerIds());
        for (Long c : wanted) {
            if (!p.getClientIds().contains(c)) {
                accounting.requireCompanyCustomer(c, p.getCompanyId());
            }
        }
        p.getClientIds().clear();
        p.getClientIds().addAll(wanted);
        activity.log(p.getCompanyId(), p.getId(), "CLIENTS_UPDATED", wanted.size() + " client(s)");
        return dto(p);
    }

    /** Soft delete; tasks, bugs and milestones stay with the deleted project and are no longer reachable. */
    @Transactional
    public void delete(Long id) {
        Project p = requireManaged(id);
        p.setDeleted(true);
        activity.log(p.getCompanyId(), p.getId(), "PROJECT_DELETED", p.getName());
    }

    // ---------- shared ----------

    /** The project of the caller's company; another company's or a deleted one answers 404 (PRJ_006). */
    @Transactional(readOnly = true)
    public Project require(Long id) {
        Project p = projects.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_006));
        TenantGuard.assertSameCompany(p.getCompanyId(), EProjectError.PRJ_006);
        if (!ProjectScope.seesAll()) {
            OwnScope.assertMine(ProjectScope.onTeam(p), EProjectError.PRJ_006);
        }
        return p;
    }

    /** For project writes: with PROJECT_MANAGE_OWN only, a project I did not create answers 404. */
    @Transactional(readOnly = true)
    public Project requireManaged(Long id) {
        Project p = require(id);
        if (!ProjectScope.managesAll()) {
            OwnScope.assertMine(ProjectScope.createdByMe(p.getCreatedBy()), EProjectError.PRJ_006);
        }
        return p;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Long daysLeft(Project p) {
        return p.getEndDate() == null ? null : ChronoUnit.DAYS.between(today(), p.getEndDate());
    }

    public static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    public ProjectDto dto(Project p) {
        return new ProjectDto(p.getId() == null ? null : p.getId().toString(), p.getCompanyId().toString(),
                p.getName(), p.getStartDate(), p.getEndDate(), p.getBudget(), p.getDescription(), p.getStatus(),
                p.overdue(today()), p.getUserIds().stream().map(String::valueOf).toList(),
                p.getClientIds().stream().map(String::valueOf).toList(), p.getCreatedDate());
    }

    private static void apply(Project p, ProjectForm f) {
        p.setName(f.name().trim());
        p.setStartDate(f.startDate());
        p.setEndDate(f.endDate());
        p.setBudget(f.budget());
        p.setDescription(f.description());
    }

    private static void checkDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw ApiException.badRequest(EProjectError.PRJ_007);
        }
    }

    /** Only ids not already on the team are asked to IAM (one call each). */
    private Set<Long> checkedTeam(Long company, Set<Long> current, List<Long> userIds) {
        Set<Long> wanted = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        for (Long u : wanted) {
            if (!current.contains(u)) {
                iam.requireCompanyUser(u, company);
            }
        }
        return wanted;
    }
}
