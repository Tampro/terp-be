package com.terp.erp.payroll.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EType;
import com.terp.erp.payroll.component.SalaryComponentService.ComponentForm;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalaryComponentServiceTest {
    private final SalaryComponentRepository repo = mock(SalaryComponentRepository.class);
    private final SalaryComponentService service = new SalaryComponentService(repo,
            mock(com.terp.erp.payroll.salary.EmployeeSalaryRepository.class));

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE");
        when(repo.save(any())).thenAnswer(i -> {
            SalaryComponent c = i.getArgument(0);
            c.setId(1L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRules() {
        var c = service.create(null, form(ECalculation.PERCENTAGE, "40"));
        assertThat(c.name()).isEqualTo("HRA");
        assertThat(c.status()).isEqualTo(SalaryComponent.EStatus.ACTIVE);
        assertCode(() -> service.create(null, form(ECalculation.PERCENTAGE, "100.01")), EPayrollError.PAY_004);
        // a fixed amount may exceed 100
        assertThat(service.create(null, form(ECalculation.FIXED, "1500000")).value()).isEqualByComparingTo("1500000");
        when(repo.nameTaken(anyLong(), anyString(), anyLong())).thenReturn(true);
        assertCode(() -> service.create(null, form(ECalculation.FIXED, "1")), EPayrollError.PAY_003);
    }

    @Test
    void otherCompanyIsNotFoundAndSuperAdminMustNameOne() {
        SalaryComponent c = new SalaryComponent();
        c.setId(2L);
        c.setCompanyId(11L);
        when(repo.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.get(2L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
        TestLogin.superAdmin();
        assertCode(() -> service.create(null, form(ECalculation.FIXED, "1")), EPayrollError.PAY_001);
    }

    @Test
    void keywordIsEscaped() {
        assertThat(Texts.kw(" 50%_off! ")).isEqualTo("%50!%!_off!!%");
        assertThat(Texts.kw(" ")).isEmpty();
    }

    private static ComponentForm form(ECalculation calc, String value) {
        return new ComponentForm(" HRA ", null, EType.EARNING, calc, new BigDecimal(value), null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EPayrollError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
