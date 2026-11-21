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
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Review Cycles page (docs/analysis/06 §7.3.3). Names unique per company (EMP_011); reviews pick an active cycle
 * (checked by {@link #usable}) and a cycle used by a review cannot be deleted (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class ReviewCycleService {
    private final ReviewCycleRepository cycles;
    private final EmployeeReviewRepository reviews;

    public record CycleDto(String id, String name, ReviewCycle.EFrequency frequency, String description,
                           EOrgStatus status, Instant createdDate, Instant updatedDate) {
        static CycleDto of(ReviewCycle c) {
            return new CycleDto(c.getId().toString(), c.getName(), c.getFrequency(), c.getDescription(),
                    c.getStatus(), c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    public record CycleForm(
            @Schema(example = "Monthly Performance Review", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ReviewCycle.EFrequency frequency,
            @Size(max = 500) String description,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EOrgStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<CycleDto> search(Long requestedCompanyId, String keyword, ReviewCycle.EFrequency frequency,
                                 EOrgStatus status, Pageable pageable) {
        return cycles.search(PerformanceSupport.company(requestedCompanyId), frequency, status,
                DepartmentService.kw(keyword), pageable).map(CycleDto::of);
    }

    @Transactional(readOnly = true)
    public List<Ref> options(Long requestedCompanyId) {
        return cycles.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(
                        PerformanceSupport.company(requestedCompanyId), EOrgStatus.ACTIVE).stream()
                .map(c -> new Ref(c.getId().toString(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public CycleDto get(Long id) {
        return CycleDto.of(require(id));
    }

    @Transactional
    public CycleDto create(Long requestedCompanyId, CycleForm form) {
        ReviewCycle c = new ReviewCycle();
        c.setCompanyId(PerformanceSupport.company(requestedCompanyId));
        apply(c, form, -1L);
        return CycleDto.of(cycles.save(c));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public CycleDto update(Long id, CycleForm form) {
        ReviewCycle c = require(id);
        apply(c, form, id);
        return CycleDto.of(c);
    }

    @Transactional
    public CycleDto changeStatus(Long id, EOrgStatus status) {
        ReviewCycle c = require(id);
        c.setStatus(status);
        return CycleDto.of(c);
    }

    @Transactional
    public void delete(Long id) {
        ReviewCycle c = require(id);
        if (reviews.existsByReviewCycleIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        c.setDeleted(true);
    }

    /** A cycle of the company for a review; inactive only when the review already uses it (EMP_054). */
    public ReviewCycle usable(Long id, Long companyId, Long current) {
        ReviewCycle c = cycles.findByIdAndDeletedFalse(id).filter(x -> x.getCompanyId().equals(companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_073));
        if (c.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(id, current)) {
            throw ApiException.badRequest(EEmployeeError.EMP_054, "review cycle");
        }
        return c;
    }

    private void apply(ReviewCycle c, CycleForm f, Long selfId) {
        String name = f.name().trim();
        if (cycles.nameTaken(c.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        c.setName(name);
        c.setFrequency(f.frequency());
        c.setDescription(PerformanceSupport.blankToNull(f.description()));
        if (f.status() != null) {
            c.setStatus(f.status());
        }
    }

    private ReviewCycle require(Long id) {
        ReviewCycle c = cycles.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_073));
        TenantGuard.assertSameCompany(c.getCompanyId(), EEmployeeError.EMP_073);
        return c;
    }
}
