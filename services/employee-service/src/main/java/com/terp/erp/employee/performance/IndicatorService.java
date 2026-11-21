package com.terp.erp.employee.performance;

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
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.performance.PerformanceSupport.Ref;
import com.terp.erp.employee.performance.PerformanceSupport.StatusCounts;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Indicators page (docs/analysis/06 §7.3.4). The category is an active indicator category of the company; names are
 * unique per company (EMP_011). Indicators rated in reviews are snapshotted there, so deleting one does not change
 * a past review.
 */
@Service
@RequiredArgsConstructor
public class IndicatorService {
    private final PerformanceIndicatorRepository indicators;
    private final PerformanceCatalogRepository catalogItems;
    private final PerformanceCatalogService catalogs;

    public record IndicatorDto(String id, Ref category, String name, String description, String measurementUnit,
                               String targetValue, BigDecimal weight, EOrgStatus status, Instant createdDate,
                               Instant updatedDate) {
    }

    public record IndicatorForm(
            @Schema(example = "Code quality", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Schema(description = "Entry of /performance/indicator-categories", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Size(max = 1000) String description,
            @Schema(example = "Percentage") @Size(max = 50) String measurementUnit,
            @Schema(example = "95%") @Size(max = 100) String targetValue,
            @Schema(description = "Weight in the review rating; omitted = 1", example = "1.5")
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") @Digits(integer = 4, fraction = 2)
            BigDecimal weight,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EOrgStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<IndicatorDto> search(Long requestedCompanyId, String keyword, Long categoryId, EOrgStatus status,
                                     Pageable pageable) {
        Page<PerformanceIndicator> page = indicators.search(PerformanceSupport.company(requestedCompanyId),
                categoryId, status, DepartmentService.kw(keyword), pageable);
        Map<Long, String> names = names(page.map(PerformanceIndicator::getCategoryId).toList());
        return page.map(i -> dto(i, names));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long categoryId) {
        return StatusCounts.of(indicators.countByStatus(PerformanceSupport.company(requestedCompanyId), categoryId,
                DepartmentService.kw(keyword)));
    }

    @Transactional(readOnly = true)
    public IndicatorDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public IndicatorDto create(Long requestedCompanyId, IndicatorForm form) {
        PerformanceIndicator i = new PerformanceIndicator();
        i.setCompanyId(PerformanceSupport.company(requestedCompanyId));
        apply(i, form, -1L);
        return detail(indicators.save(i));
    }

    /** Full replacement; weight omitted = 1, status omitted = kept. */
    @Transactional
    public IndicatorDto update(Long id, IndicatorForm form) {
        PerformanceIndicator i = require(id);
        apply(i, form, id);
        return detail(i);
    }

    @Transactional
    public IndicatorDto changeStatus(Long id, EOrgStatus status) {
        PerformanceIndicator i = require(id);
        i.setStatus(status);
        return detail(i);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(PerformanceIndicator i, IndicatorForm f, Long selfId) {
        String name = f.name().trim();
        if (indicators.nameTaken(i.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        i.setCategoryId(catalogs.usable(EPerformanceCatalogKind.INDICATOR_CATEGORY, f.categoryId(), i.getCompanyId(),
                i.getCategoryId()).getId());
        i.setName(name);
        i.setDescription(PerformanceSupport.blankToNull(f.description()));
        i.setMeasurementUnit(PerformanceSupport.blankToNull(f.measurementUnit()));
        i.setTargetValue(PerformanceSupport.blankToNull(f.targetValue()));
        i.setWeight(f.weight() == null ? BigDecimal.ONE : f.weight());
        if (f.status() != null) {
            i.setStatus(f.status());
        }
    }

    private Map<Long, String> names(List<Long> ids) {
        return catalogItems.findAllById(ids).stream()
                .collect(Collectors.toMap(PerformanceCatalog::getId, PerformanceCatalog::getName));
    }

    private IndicatorDto detail(PerformanceIndicator i) {
        return dto(i, names(List.of(i.getCategoryId())));
    }

    private static IndicatorDto dto(PerformanceIndicator i, Map<Long, String> names) {
        return new IndicatorDto(i.getId().toString(), new Ref(i.getCategoryId().toString(),
                names.get(i.getCategoryId())), i.getName(), i.getDescription(), i.getMeasurementUnit(),
                i.getTargetValue(), i.getWeight(), i.getStatus(), i.getCreatedDate(), i.getUpdatedDate());
    }

    private PerformanceIndicator require(Long id) {
        PerformanceIndicator i = indicators.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_072));
        TenantGuard.assertSameCompany(i.getCompanyId(), EEmployeeError.EMP_072);
        return i;
    }
}
