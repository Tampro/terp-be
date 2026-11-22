package com.terp.erp.payroll.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.catalog.PayrollType.EKind;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.loan.EmployeeLoanRepository;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.Locale;

/** Loan / allowance / deduction types (manual 15 Setup). Code unique per company and kind (PAY_028). */
@Service
@RequiredArgsConstructor
public class PayrollTypeService {
    private final PayrollTypeRepository types;
    private final EmployeeLoanRepository loans;

    public record TypeDto(String id, EKind kind, String name, String code, boolean active, Instant createdDate,
                          Instant updatedDate) {
        static TypeDto of(PayrollType t) {
            return new TypeDto(t.getId().toString(), t.getKind(), t.getName(), t.getCode(), t.isActive(),
                    t.getCreatedDate(), t.getUpdatedDate());
        }
    }

    public record TypeForm(
            @Schema(example = "LOAN", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EKind kind,
            @Schema(example = "Personal loan", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Schema(description = "Unique per company and kind (case-insensitive); stored upper-case",
                    example = "PERSONAL", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50)
            String code,
            @Schema(description = "Omitted = true on create, kept on edit") Boolean active) {
    }

    @Transactional(readOnly = true)
    public Page<TypeDto> search(Long requestedCompanyId, EKind kind, Boolean active, String keyword,
                                Pageable pageable) {
        return types.search(Texts.company(requestedCompanyId), kind, active, Texts.kw(keyword), pageable)
                .map(TypeDto::of);
    }

    @Transactional(readOnly = true)
    public TypeDto get(Long id) {
        return TypeDto.of(require(id));
    }

    @Transactional
    public TypeDto create(Long requestedCompanyId, TypeForm form) {
        PayrollType t = new PayrollType();
        t.setCompanyId(Texts.company(requestedCompanyId));
        apply(t, form, -1L);
        return TypeDto.of(types.save(t));
    }

    /** Full replacement; a LOAN type used by loans keeps its kind (PAY_005). */
    @Transactional
    public TypeDto update(Long id, TypeForm form) {
        PayrollType t = require(id);
        if (t.getKind() == EKind.LOAN && form.kind() != EKind.LOAN && loans.existsByLoanTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EPayrollError.PAY_005);
        }
        apply(t, form, id);
        return TypeDto.of(t);
    }

    @Transactional
    public void delete(Long id) {
        PayrollType t = require(id);
        if (t.getKind() == EKind.LOAN && loans.existsByLoanTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EPayrollError.PAY_005);
        }
        t.setDeleted(true);
    }

    /** A type of the caller's company; 404 PAY_027 otherwise. */
    public PayrollType require(Long id) {
        PayrollType t = types.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_027));
        TenantGuard.assertSameCompany(t.getCompanyId(), EPayrollError.PAY_027);
        return t;
    }

    private void apply(PayrollType t, TypeForm f, Long selfId) {
        String code = f.code().trim().toUpperCase(Locale.ROOT);
        if (types.codeTaken(t.getCompanyId(), f.kind(), code, selfId)) {
            throw ApiException.badRequest(EPayrollError.PAY_028);
        }
        t.setKind(f.kind());
        t.setName(f.name().trim());
        t.setCode(code);
        if (f.active() != null) {
            t.setActive(f.active());
        }
    }
}
