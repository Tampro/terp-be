package com.terp.erp.payroll.component;

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
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EStatus;
import com.terp.erp.payroll.component.SalaryComponent.EType;
import com.terp.erp.payroll.salary.EmployeeSalaryRepository;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Salary Components page (docs/analysis/06 §8.1.4). Names unique per company (PAY_003); a percentage is of the basic
 * salary and lies in 0–100 (PAY_004). Components used by a salary structure cannot be deleted (PAY_005).
 */
@Service
@RequiredArgsConstructor
public class SalaryComponentService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SalaryComponentRepository components;
    private final EmployeeSalaryRepository salaries;

    public record ComponentDto(String id, String name, String description, EType type, ECalculation calculation,
                               BigDecimal value, EStatus status, Instant createdDate, Instant updatedDate) {
        static ComponentDto of(SalaryComponent c) {
            return new ComponentDto(c.getId().toString(), c.getName(), c.getDescription(), c.getType(),
                    c.getCalculation(), c.getValue(), c.getStatus(), c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    public record ComponentForm(
            @Schema(example = "House Rent Allowance", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Size(max = 500) String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EType type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ECalculation calculation,
            @Schema(description = "Amount (FIXED, company currency) or % of the basic salary (PERCENTAGE, 0–100)",
                    example = "10", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            @Digits(integer = 13, fraction = 2) BigDecimal value,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<ComponentDto> search(Long requestedCompanyId, String keyword, EType type, ECalculation calculation,
                                     EStatus status, Pageable pageable) {
        return components.search(Texts.company(requestedCompanyId), type, calculation, status, Texts.kw(keyword),
                pageable).map(ComponentDto::of);
    }

    /** Active components, earnings first, for the salary structure form. */
    @Transactional(readOnly = true)
    public List<ComponentDto> options(Long requestedCompanyId) {
        return components.findByCompanyIdAndStatusAndDeletedFalseOrderByTypeDescNameAsc(
                Texts.company(requestedCompanyId), EStatus.ACTIVE).stream().map(ComponentDto::of).toList();
    }

    @Transactional(readOnly = true)
    public ComponentDto get(Long id) {
        return ComponentDto.of(require(id));
    }

    @Transactional
    public ComponentDto create(Long requestedCompanyId, ComponentForm form) {
        SalaryComponent c = new SalaryComponent();
        c.setCompanyId(Texts.company(requestedCompanyId));
        apply(c, form, -1L);
        return ComponentDto.of(components.save(c));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public ComponentDto update(Long id, ComponentForm form) {
        SalaryComponent c = require(id);
        apply(c, form, id);
        return ComponentDto.of(c);
    }

    @Transactional
    public ComponentDto changeStatus(Long id, EStatus status) {
        SalaryComponent c = require(id);
        c.setStatus(status);
        return ComponentDto.of(c);
    }

    @Transactional
    public void delete(Long id) {
        SalaryComponent c = require(id);
        if (salaries.usesComponent(id)) {
            throw ApiException.badRequest(EPayrollError.PAY_005);
        }
        c.setDeleted(true);
    }

    private void apply(SalaryComponent c, ComponentForm f, Long selfId) {
        String name = f.name().trim();
        if (components.nameTaken(c.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EPayrollError.PAY_003);
        }
        if (f.calculation() == ECalculation.PERCENTAGE && f.value().compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest(EPayrollError.PAY_004);
        }
        c.setName(name);
        c.setDescription(Texts.blankToNull(f.description()));
        c.setType(f.type());
        c.setCalculation(f.calculation());
        c.setValue(f.value());
        if (f.status() != null) {
            c.setStatus(f.status());
        }
    }

    SalaryComponent require(Long id) {
        SalaryComponent c = components.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_002));
        TenantGuard.assertSameCompany(c.getCompanyId(), EPayrollError.PAY_002);
        return c;
    }
}
