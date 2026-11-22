package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.income.CashVoucherRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/** Revenue and expense categories; the kind decides which not-found error and endpoints apply. */
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final AccountCategoryRepository categories;
    private final ChartAccountRepository accounts;
    private final CashVoucherRepository vouchers;

    public record CategoryForm(
            @Schema(example = "Phí dịch vụ", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(description = "Unique per company and kind", example = "SERVICE_FEE",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,30}")
            String code,
            @Schema(description = "Default ledger account of records in this category", example = "40",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long glAccountId,
            @Schema(example = "Service income") @Size(max = 500) String description,
            @Schema(example = "true") boolean enabled) {
    }

    public record CategoryDto(String id, String companyId, ECategoryKind kind, String code, String name,
                              String glAccountId, String description, boolean enabled, Instant createdDate) {
    }

    @Transactional(readOnly = true)
    public Page<CategoryDto> search(ECategoryKind kind, Long requestedCompanyId, Boolean enabled, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return categories.search(companyId, kind, enabled, pageable).map(CategoryService::dto);
    }

    @Transactional(readOnly = true)
    public CategoryDto get(ECategoryKind kind, Long id) {
        return dto(require(kind, id));
    }

    @Transactional
    public CategoryDto create(ECategoryKind kind, Long requestedCompanyId, CategoryForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        AccountCategory c = new AccountCategory();
        c.setCompanyId(companyId);
        c.setKind(kind);
        apply(c, form);
        return dto(categories.save(c));
    }

    /** Full replacement. */
    @Transactional
    public CategoryDto update(ECategoryKind kind, Long id, CategoryForm form) {
        AccountCategory c = require(kind, id);
        apply(c, form);
        return dto(c);
    }

    @Transactional
    public void delete(ECategoryKind kind, Long id) {
        AccountCategory c = require(kind, id);
        if (vouchers.existsByCategoryId(c.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_033);
        }
        categories.delete(c);
    }

    /** A category of this kind in the caller's company (ACC_014 / ACC_015, 404 otherwise). */
    public AccountCategory require(ECategoryKind kind, Long id) {
        EAccountingError notFound = kind == ECategoryKind.REVENUE ? EAccountingError.ACC_014 : EAccountingError.ACC_015;
        AccountCategory c = categories.findById(id).filter(x -> x.getKind() == kind)
                .orElseThrow(() -> ApiException.notFound(notFound));
        TenantGuard.assertSameCompany(c.getCompanyId(), notFound);
        return c;
    }

    private void apply(AccountCategory c, CategoryForm f) {
        String code = f.code().trim();
        if (categories.existsByCompanyIdAndKindAndCodeAndIdNot(c.getCompanyId(), c.getKind(), code,
                c.getId() == null ? -1L : c.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_016);
        }
        if (accounts.findById(f.glAccountId())
                .filter(a -> a.isActive() && Objects.equals(a.getCompanyId(), c.getCompanyId())).isEmpty()) {
            throw ApiException.badRequest(EAccountingError.ACC_020, f.glAccountId());
        }
        c.setCode(code);
        c.setName(f.name().trim());
        c.setGlAccountId(f.glAccountId());
        c.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
        c.setEnabled(f.enabled());
    }

    private static CategoryDto dto(AccountCategory c) {
        return new CategoryDto(c.getId().toString(), c.getCompanyId().toString(), c.getKind(), c.getCode(),
                c.getName(), c.getGlAccountId().toString(), c.getDescription(), c.isEnabled(), c.getCreatedDate());
    }
}
