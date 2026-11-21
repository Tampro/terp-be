package com.terp.erp.payroll.salary;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.client.EmployeeClient.Employee;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.component.SalaryComponent;
import com.terp.erp.payroll.component.SalaryComponentRepository;
import com.terp.erp.payroll.salary.EmployeeSalary.EStatus;
import com.terp.erp.payroll.salary.SalaryMath.Totals;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee Salaries page (docs/analysis/06 §8.1.3). The employee is checked in employee-service as the caller (another
 * company's answers EMP_001); one ACTIVE structure per employee (PAY_008); components are the company's, active when
 * newly chosen (PAY_009), each once (PAY_010).
 */
@Service
@RequiredArgsConstructor
public class EmployeeSalaryService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final EmployeeSalaryRepository salaries;
    private final SalaryComponentRepository components;
    private final EmployeeClient employees;

    /** The employee as shown; photo and current name from employee-service, the copy when it does not answer it. */
    public record EmployeeInfo(String id, String employeeNo, String fullName, String email, String profileImageUrl) {
    }

    /** structure only on the details. */
    public record SalaryDto(String id, EmployeeInfo employee, BigDecimal basicSalary, EStatus status, String notes,
                            Totals structure, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record LineForm(
            @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long componentId,
            @Schema(description = "Overrides the component's value (same unit); omitted = the component's",
                    example = "1500000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal value) {
    }

    public record SalaryForm(
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Per month, company currency", example = "15000000",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            @Digits(integer = 13, fraction = 2) BigDecimal basicSalary,
            @Schema(description = "Full set of components") List<@Valid @NotNull LineForm> components,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status,
            @Size(max = 1000) String notes) {
    }

    @Transactional(readOnly = true)
    public Page<SalaryDto> search(Long requestedCompanyId, String keyword, Long employeeId, EStatus status,
                                  Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<EmployeeSalary> page = salaries.search(companyId, employeeId, status, Texts.kw(keyword), pageable);
        Map<Long, Employee> people = page.isEmpty() ? Map.of()
                : employees.byIds(page.map(EmployeeSalary::getEmployeeId).toList(), companyId);
        return page.map(s -> dto(s, people.get(s.getEmployeeId()), null));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : salaries.countByStatus(Texts.company(requestedCompanyId), employeeId, Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public SalaryDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public SalaryDto create(SalaryForm form) {
        EmployeeSalary s = new EmployeeSalary();
        apply(s, form);
        return detail(salaries.save(s));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public SalaryDto update(Long id, SalaryForm form) {
        EmployeeSalary s = require(id);
        apply(s, form);
        return detail(s);
    }

    /** Toggle Status; activating needs no other active structure of the employee (PAY_008). */
    @Transactional
    public SalaryDto changeStatus(Long id, EStatus status) {
        EmployeeSalary s = require(id);
        assertSingleActive(s.getEmployeeId(), status, s.getId());
        s.setStatus(status);
        return detail(s);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(EmployeeSalary s, SalaryForm f) {
        Employee e = employees.employee(f.employeeId());
        Long companyId = Long.valueOf(e.companyId());
        // employee-service already refused another company's employee; this guards a super admin's mix-up too
        if (s.getCompanyId() != null && !s.getCompanyId().equals(companyId)) {
            throw ApiException.notFound(EPayrollError.PAY_007);
        }
        EStatus status = f.status() != null ? f.status() : s.getStatus();
        assertSingleActive(f.employeeId(), status, s.getId() == null ? -1L : s.getId());
        Set<Long> current = s.getLines().stream().map(EmployeeSalary.Line::getComponentId).collect(Collectors.toSet());
        List<LineForm> lines = f.components() == null ? List.of() : f.components();
        Set<Long> seen = new HashSet<>();
        Map<Long, SalaryComponent> byId = components.findAllById(lines.stream().map(LineForm::componentId).toList())
                .stream().collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));
        for (LineForm l : lines) {
            if (!seen.add(l.componentId())) {
                throw ApiException.badRequest(EPayrollError.PAY_010);
            }
            SalaryComponent c = byId.get(l.componentId());
            if (c == null || c.isDeleted() || !c.getCompanyId().equals(companyId)) {
                throw ApiException.badRequest(EPayrollError.PAY_002);
            }
            if (c.getStatus() == SalaryComponent.EStatus.INACTIVE && !current.contains(c.getId())) {
                throw ApiException.badRequest(EPayrollError.PAY_009, c.getName());
            }
            if (l.value() != null && c.getCalculation() == SalaryComponent.ECalculation.PERCENTAGE
                    && l.value().compareTo(HUNDRED) > 0) {
                throw ApiException.badRequest(EPayrollError.PAY_004);
            }
        }
        s.setCompanyId(companyId);
        s.setEmployeeId(Long.valueOf(e.id()));
        s.setEmployeeName(e.fullName());
        s.setEmployeeNo(e.employeeNo());
        s.setEmployeeEmail(e.email());
        s.setBasicSalary(f.basicSalary());
        s.setStatus(status);
        s.setNotes(Texts.blankToNull(f.notes()));
        s.getLines().clear();
        lines.forEach(l -> s.getLines().add(new EmployeeSalary.Line(l.componentId(), l.value())));
    }

    private void assertSingleActive(Long employeeId, EStatus status, Long selfId) {
        if (status == EStatus.ACTIVE
                && salaries.existsByEmployeeIdAndStatusAndDeletedFalseAndIdNot(employeeId, EStatus.ACTIVE, selfId)) {
            throw ApiException.badRequest(EPayrollError.PAY_008);
        }
    }

    /** The structure with its amounts (also used by payroll processing). */
    public Totals structure(EmployeeSalary s) {
        Map<Long, SalaryComponent> byId = components.findAllById(s.getLines().stream()
                .map(EmployeeSalary.Line::getComponentId).toList()).stream()
                .collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));
        return SalaryMath.totals(s.getBasicSalary(), s.getLines(), byId);
    }

    private SalaryDto detail(EmployeeSalary s) {
        Employee e = employees.byIds(List.of(s.getEmployeeId()), s.getCompanyId()).get(s.getEmployeeId());
        return dto(s, e, structure(s));
    }

    private static SalaryDto dto(EmployeeSalary s, Employee e, Totals structure) {
        EmployeeInfo info = new EmployeeInfo(s.getEmployeeId().toString(),
                e != null ? e.employeeNo() : s.getEmployeeNo(), e != null ? e.fullName() : s.getEmployeeName(),
                e != null ? e.email() : s.getEmployeeEmail(), e == null ? null : e.profileImageUrl());
        return new SalaryDto(s.getId().toString(), info, s.getBasicSalary(), s.getStatus(), s.getNotes(), structure,
                s.getCreatedDate(), s.getUpdatedDate());
    }

    EmployeeSalary require(Long id) {
        EmployeeSalary s = salaries.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_007));
        TenantGuard.assertSameCompany(s.getCompanyId(), EPayrollError.PAY_007);
        return s;
    }
}
