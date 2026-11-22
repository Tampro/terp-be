package com.terp.erp.payroll.loan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.catalog.PayrollType;
import com.terp.erp.payroll.catalog.PayrollTypeRepository;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.loan.EmployeeLoanService.LoanForm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeLoanServiceTest {
    private final EmployeeLoanRepository loans = mock(EmployeeLoanRepository.class);
    private final LoanRepaymentRepository repayments = mock(LoanRepaymentRepository.class);
    private final PayrollTypeRepository types = mock(PayrollTypeRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final EmployeeLoanService service = new EmployeeLoanService(loans, repayments, types, employees);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE");
        PayrollType t = new PayrollType();
        t.setId(4L);
        t.setCompanyId(10L);
        t.setKind(PayrollType.EKind.LOAN);
        t.setName("Personal");
        when(types.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(t));
        when(employees.employee(7L)).thenReturn(new EmployeeClient.Employee("7", "10", "E7", "Test Seven", null,
                null, "ACTIVE", null, null));
        when(loans.save(any())).thenAnswer(i -> {
            EmployeeLoan l = i.getArgument(0);
            l.setId(1L);
            return l;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void installmentFromNumberOfInstallmentsAndFullBalance() {
        var dto = service.create(null, 7L, form(null, 3));
        // 10,000,000 / 3 rounded up
        assertThat(dto.monthlyInstallment()).isEqualByComparingTo("3333334");
        assertThat(dto.remainingBalance()).isEqualByComparingTo("10000000");
        assertThat(dto.status()).isEqualTo(EmployeeLoan.EStatus.ACTIVE);
    }

    @Test
    void formRules() {
        assertCode(() -> service.create(null, 7L, form(null, null)), EPayrollError.PAY_030);
        when(employees.employee(8L)).thenReturn(new EmployeeClient.Employee("8", "11", "E8", "Other", null, null,
                "ACTIVE", null, null));
        assertCode(() -> service.create(null, 8L, form(new BigDecimal("1000"), null)), EPayrollError.PAY_029);
    }

    @Test
    void otherCompanysLoanIsNotFound() {
        EmployeeLoan l = new EmployeeLoan();
        l.setId(2L);
        l.setCompanyId(11L);
        when(loans.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(l));
        assertCode(() -> service.get(2L), EPayrollError.PAY_029);
        assertCode(() -> service.delete(2L), EPayrollError.PAY_029);
        assertCode(() -> service.update(2L, form(new BigDecimal("1000"), null)), EPayrollError.PAY_029);
    }

    @Test
    void repaidLoanCannotBeDeletedNorReducedBelowRepaid() {
        EmployeeLoan l = new EmployeeLoan();
        l.setId(2L);
        l.setCompanyId(10L);
        when(loans.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(l));
        when(repayments.existsByLoanId(2L)).thenReturn(true);
        when(repayments.repaid(2L)).thenReturn(new BigDecimal("12000000"));
        assertCode(() -> service.delete(2L), EPayrollError.PAY_035);
        assertCode(() -> service.update(2L, form(new BigDecimal("1000"), null)), EPayrollError.PAY_035);
    }

    private static LoanForm form(BigDecimal installment, Integer count) {
        return new LoanForm("Laptop", 4L, new BigDecimal("10000000"), installment, count, LocalDate.of(2026, 11, 1),
                null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EPayrollError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
