package com.terp.erp.employee.performance;

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
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.performance.PerformanceSupport.Ref;
import com.terp.erp.employee.performance.PerformanceSupport.StatusCounts;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Goal Types (§7.3.5) and Indicator Categories (§7.3.6). An entry used by a record cannot be deleted (EMP_010);
 * indicators use the categories, goals the goal types.
 */
@Service
@RequiredArgsConstructor
public class PerformanceCatalogService {
    private final PerformanceCatalogRepository items;
    private final PerformanceIndicatorRepository indicators;
    private final EmployeeGoalRepository goals;

    public record CatalogDto(String id, EPerformanceCatalogKind kind, String name, String description,
                             EOrgStatus status, Instant createdDate, Instant updatedDate) {
        static CatalogDto of(PerformanceCatalog c) {
            return new CatalogDto(c.getId().toString(), c.getKind(), c.getName(), c.getDescription(), c.getStatus(),
                    c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    public record CatalogForm(
            @Schema(example = "Job Performance", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Size(max = 500) String description,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EOrgStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EOrgStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<CatalogDto> search(EPerformanceCatalogKind kind, Long requestedCompanyId, String keyword,
                                   EOrgStatus status, Pageable pageable) {
        return items.search(PerformanceSupport.company(requestedCompanyId), kind, status,
                DepartmentService.kw(keyword), pageable).map(CatalogDto::of);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(EPerformanceCatalogKind kind, Long requestedCompanyId, String keyword) {
        return StatusCounts.of(items.countByStatus(PerformanceSupport.company(requestedCompanyId), kind,
                DepartmentService.kw(keyword)));
    }

    @Transactional(readOnly = true)
    public List<Ref> options(EPerformanceCatalogKind kind, Long requestedCompanyId) {
        return items.findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(
                        PerformanceSupport.company(requestedCompanyId), kind, EOrgStatus.ACTIVE).stream()
                .map(c -> new Ref(c.getId().toString(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public CatalogDto get(EPerformanceCatalogKind kind, Long id) {
        return CatalogDto.of(require(kind, id));
    }

    @Transactional
    public CatalogDto create(EPerformanceCatalogKind kind, Long requestedCompanyId, CatalogForm form) {
        PerformanceCatalog c = new PerformanceCatalog();
        c.setCompanyId(PerformanceSupport.company(requestedCompanyId));
        c.setKind(kind);
        apply(c, form, -1L);
        return CatalogDto.of(items.save(c));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public CatalogDto update(EPerformanceCatalogKind kind, Long id, CatalogForm form) {
        PerformanceCatalog c = require(kind, id);
        apply(c, form, id);
        return CatalogDto.of(c);
    }

    @Transactional
    public CatalogDto changeStatus(EPerformanceCatalogKind kind, Long id, EOrgStatus status) {
        PerformanceCatalog c = require(kind, id);
        c.setStatus(status);
        return CatalogDto.of(c);
    }

    @Transactional
    public void delete(EPerformanceCatalogKind kind, Long id) {
        PerformanceCatalog c = require(kind, id);
        boolean used = switch (kind) {
            case INDICATOR_CATEGORY -> indicators.existsByCategoryIdAndDeletedFalse(id);
            case GOAL_TYPE -> goals.existsByGoalTypeIdAndDeletedFalse(id);
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
    public PerformanceCatalog usable(EPerformanceCatalogKind kind, Long id, Long companyId, Long current) {
        PerformanceCatalog c = items.findByIdAndKindAndDeletedFalse(id, kind)
                .filter(x -> x.getCompanyId().equals(companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_071));
        if (c.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(id, current)) {
            throw ApiException.badRequest(EEmployeeError.EMP_054, kind.label());
        }
        return c;
    }

    private void apply(PerformanceCatalog c, CatalogForm f, Long selfId) {
        String name = f.name().trim();
        if (items.nameTaken(c.getCompanyId(), c.getKind(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        c.setName(name);
        c.setDescription(PerformanceSupport.blankToNull(f.description()));
        if (f.status() != null) {
            c.setStatus(f.status());
        }
    }

    private PerformanceCatalog require(EPerformanceCatalogKind kind, Long id) {
        PerformanceCatalog c = items.findByIdAndKindAndDeletedFalse(id, kind)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_071));
        TenantGuard.assertSameCompany(c.getCompanyId(), EEmployeeError.EMP_071);
        return c;
    }
}
