package com.terp.erp.payroll.salary;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.component.SalaryComponent;
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EType;
import com.terp.erp.payroll.component.SalaryComponentRepository;
import com.terp.erp.payroll.salary.EmployeeSalaryService.LineForm;
import com.terp.erp.payroll.salary.EmployeeSalaryService.SalaryForm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeSalaryServiceTest {
    private final EmployeeSalaryRepository salaries = mock(EmployeeSalaryRepository.class);
    private final SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final EmployeeSalaryService service = new EmployeeSalaryService(salaries, components, employees);

    private final SalaryComponent hra = component(1L, EType.EARNING, ECalculation.PERCENTAGE, "40");
    private final SalaryComponent transport = component(2L, EType.EARNING, ECalculation.FIXED, "500000");
    private final SalaryComponent pf = component(3L, EType.DEDUCTION, ECalculation.PERCENTAGE, "12");

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE");
        when(employees.employee(7L)).thenReturn(new EmployeeClient.Employee("7", "10", "EMP000007", "Test Seven",
                "seven@example.invalid", null, "ACTIVE", null, null));
        when(employees.byIds(any(), any())).thenReturn(Map.of());
        when(components.findAllById(any())).thenReturn(List.of(hra, transport, pf));
        when(salaries.save(any())).thenAnswer(i -> {
            EmployeeSalary s = i.getArgument(0);
            s.setId(9L);
            return s;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void structureAmounts() {
        var s = service.create(form(List.of(new LineForm(1L, null), new LineForm(2L, null),
                new LineForm(3L, new BigDecimal("10")))));
        var t = s.structure();
        // basic 15,000,000; HRA 40% = 6,000,000; transport 500,000; PF overridden to 10% = 1,500,000
        assertThat(t.totalEarnings()).isEqualByComparingTo("21500000");
        assertThat(t.totalDeductions()).isEqualByComparingTo("1500000");
        assertThat(t.net()).isEqualByComparingTo("20000000");
        assertThat(s.employee().fullName()).isEqualTo("Test Seven");
    }

    @Test
    void formRules() {
        assertCode(() -> service.create(form(List.of(new LineForm(1L, null), new LineForm(1L, null)))),
                EPayrollError.PAY_010);
        assertCode(() -> service.create(form(List.of(new LineForm(3L, new BigDecimal("101"))))),
                EPayrollError.PAY_004);
        pf.setStatus(SalaryComponent.EStatus.INACTIVE);
        assertCode(() -> service.create(form(List.of(new LineForm(3L, null)))), EPayrollError.PAY_009);
        pf.setCompanyId(11L);
        assertCode(() -> service.create(form(List.of(new LineForm(3L, null)))), EPayrollError.PAY_002);
        when(salaries.existsByEmployeeIdAndStatusAndDeletedFalseAndIdNot(eq(7L), eq(EmployeeSalary.EStatus.ACTIVE),
                anyLong())).thenReturn(true);
        assertCode(() -> service.create(form(List.of())), EPayrollError.PAY_008);
        // an inactive structure can coexist
        assertThat(service.create(new SalaryForm(7L, BigDecimal.TEN, List.of(), EmployeeSalary.EStatus.INACTIVE,
                null)).status()).isEqualTo(EmployeeSalary.EStatus.INACTIVE);
    }

    @Test
    void roundingHalfUp() {
        var t = SalaryMath.totals(new BigDecimal("10000001"), List.of(new EmployeeSalary.Line(3L, null)),
                Map.of(3L, pf));
        // 12% of 10,000,001 = 1,200,000.12 -> 1,200,000
        assertThat(t.totalDeductions()).isEqualByComparingTo("1200000");
    }

    private static SalaryForm form(List<LineForm> lines) {
        return new SalaryForm(7L, new BigDecimal("15000000"), lines, null, " Contract 2026 ");
    }

    private static SalaryComponent component(Long id, EType type, ECalculation calc, String value) {
        SalaryComponent c = new SalaryComponent();
        c.setId(id);
        c.setCompanyId(10L);
        c.setName("C" + id);
        c.setType(type);
        c.setCalculation(calc);
        c.setValue(new BigDecimal(value));
        return c;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EPayrollError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
