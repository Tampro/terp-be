package com.terp.erp.employee.lifecycle;

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
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The company-managed lifecycle lists (PO, 2026-11-20). An entry used by a record cannot be deleted (EMP_010); the
 * pages using a list check "in use" here as they are built (warnings, terminations, trip expenses, complaints).
 */
@Service
@RequiredArgsConstructor
public class LifecycleCatalogService {
    private final LifecycleCatalogRepository items;
    private final EmployeeWarningRepository warnings;
    private final EmployeeTerminationRepository terminations;
    private final TripExpenseRepository tripExpenses;
    private final EmployeeComplaintRepository complaints;

    public record CatalogDto(String id, ELifecycleCatalogKind kind, String name, String description,
                             EOrgStatus status, Instant createdDate, Instant updatedDate) {
        static CatalogDto of(LifecycleCatalog c) {
            return new CatalogDto(c.getId().toString(), c.getKind(), c.getName(), c.getDescription(), c.getStatus(),
                    c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    public record CatalogForm(
            @Schema(example = "Attendance", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Size(max = 500) String description,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EOrgStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EOrgStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<CatalogDto> search(ELifecycleCatalogKind kind, Long requestedCompanyId, String keyword,
                                   EOrgStatus status, Pageable pageable) {
        return items.search(LifecycleSupport.company(requestedCompanyId), kind, status, DepartmentService.kw(keyword),
                pageable).map(CatalogDto::of);
    }

    @Transactional(readOnly = true)
    public List<Ref> options(ELifecycleCatalogKind kind, Long requestedCompanyId) {
        return items.findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(
                        LifecycleSupport.company(requestedCompanyId), kind, EOrgStatus.ACTIVE).stream()
                .map(c -> new Ref(c.getId().toString(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public CatalogDto get(ELifecycleCatalogKind kind, Long id) {
        return CatalogDto.of(require(kind, id));
    }

    @Transactional
    public CatalogDto create(ELifecycleCatalogKind kind, Long requestedCompanyId, CatalogForm form) {
        LifecycleCatalog c = new LifecycleCatalog();
        c.setCompanyId(LifecycleSupport.company(requestedCompanyId));
        c.setKind(kind);
        apply(c, form, -1L);
        return CatalogDto.of(items.save(c));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public CatalogDto update(ELifecycleCatalogKind kind, Long id, CatalogForm form) {
        LifecycleCatalog c = require(kind, id);
        apply(c, form, id);
        return CatalogDto.of(c);
    }

    @Transactional
    public CatalogDto changeStatus(ELifecycleCatalogKind kind, Long id, EOrgStatus status) {
        LifecycleCatalog c = require(kind, id);
        c.setStatus(status);
        return CatalogDto.of(c);
    }

    @Transactional
    public void delete(ELifecycleCatalogKind kind, Long id) {
        LifecycleCatalog c = require(kind, id);
        boolean used = switch (kind) {
            case WARNING_TYPE -> warnings.existsByWarningTypeIdAndDeletedFalse(id);
            case WARNING_SEVERITY -> warnings.existsBySeverityIdAndDeletedFalse(id);
            case TERMINATION_TYPE -> terminations.existsByTerminationTypeIdAndDeletedFalse(id);
            case EXPENSE_TYPE -> tripExpenses.existsByExpenseTypeIdAndDeletedFalse(id);
            case COMPLAINT_TYPE -> complaints.existsByComplaintTypeIdAndDeletedFalse(id);
            case RESOLUTION_TYPE -> complaints.existsByResolutionTypeIdAndDeletedFalse(id);
        };
        if (used) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        c.setDeleted(true);
    }

    /**
     * An entry of the company for a record; inactive only when the record already uses it (EMP_054).
     * @param current the id the record has today, or null
     */
    public LifecycleCatalog usable(ELifecycleCatalogKind kind, Long id, Long companyId, Long current) {
        LifecycleCatalog c = items.findByIdAndKindAndDeletedFalse(id, kind)
                .filter(x -> x.getCompanyId().equals(companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_050));
        if (c.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(id, current)) {
            throw ApiException.badRequest(EEmployeeError.EMP_054, kind.label());
        }
        return c;
    }

    private void apply(LifecycleCatalog c, CatalogForm f, Long selfId) {
        String name = f.name().trim();
        if (items.nameTaken(c.getCompanyId(), c.getKind(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        c.setName(name);
        c.setDescription(AwardService.blankToNull(f.description()));
        if (f.status() != null) {
            c.setStatus(f.status());
        }
    }

    private LifecycleCatalog require(ELifecycleCatalogKind kind, Long id) {
        LifecycleCatalog c = items.findByIdAndKindAndDeletedFalse(id, kind)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_050));
        TenantGuard.assertSameCompany(c.getCompanyId(), EEmployeeError.EMP_050);
        return c;
    }
}
