package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/** Goal categories (manual §14): code unique per company among live rows; soft delete. */
@Service
@RequiredArgsConstructor
public class GoalCategoryService {
    private final GoalCategoryRepository categories;

    public record CategoryForm(
            @Schema(example = "SAVE", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 30)
            String code,
            @Schema(example = "Savings", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(description = "null = true", example = "true") Boolean active) {
    }

    public record CategoryDto(String id, String companyId, String code, String name, boolean active,
                              Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<CategoryDto> search(Long requestedCompanyId, Boolean active, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return categories.search(companyId, active, pageable).map(GoalCategoryService::dto);
    }

    @Transactional(readOnly = true)
    public CategoryDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public CategoryDto create(Long requestedCompanyId, CategoryForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        GoalCategory c = new GoalCategory();
        c.setCompanyId(companyId);
        apply(c, form, -1L);
        return dto(categories.save(c));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryForm form) {
        GoalCategory c = require(id);
        apply(c, form, id);
        return dto(c);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** For goals: the category must be a live one of the goal's company, else 404. */
    GoalCategory require(Long id) {
        GoalCategory c = categories.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_078));
        TenantGuard.assertSameCompany(c.getCompanyId(), EAccountingError.ACC_078);
        return c;
    }

    private void apply(GoalCategory c, CategoryForm f, Long selfId) {
        String code = f.code().trim().toUpperCase(Locale.ROOT);
        if (categories.existsByCompanyIdAndCodeAndDeletedFalseAndIdNot(c.getCompanyId(), code, selfId)) {
            throw ApiException.badRequest(EAccountingError.ACC_079);
        }
        c.setCode(code);
        c.setName(f.name().trim());
        c.setActive(f.active() == null || f.active());
    }

    private static CategoryDto dto(GoalCategory c) {
        return new CategoryDto(c.getId() == null ? null : c.getId().toString(), c.getCompanyId().toString(),
                c.getCode(), c.getName(), c.isActive(), c.getCreatedDate(), c.getUpdatedDate());
    }
}
