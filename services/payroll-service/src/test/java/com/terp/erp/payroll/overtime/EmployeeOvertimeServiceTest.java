package com.terp.erp.payroll.overtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.overtime.EmployeeOvertime.EStatus;
import com.terp.erp.payroll.overtime.EmployeeOvertimeService.OvertimeForm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeOvertimeServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final EmployeeOvertimeRepository repo = mock(EmployeeOvertimeRepository.class);
    private final EmployeeOvertimeService service = new EmployeeOvertimeService(repo, mock(EmployeeClient.class));
    private EmployeeOvertime o;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE");
        o = new EmployeeOvertime();
        o.setId(9L);
        o.setCompanyId(10L);
        o.setEmployeeId(7L);
        o.setTitle("Closing");
        o.setDays(BigDecimal.ONE);
        o.setHours(BigDecimal.ONE);
        o.setRate(BigDecimal.TEN);
        o.setStartDate(DAY);
        o.setEndDate(DAY);
        o.setStatus(EStatus.PENDING);
        when(repo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(o));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void editableWhilePendingThenDecidedOnce() {
        assertThat(service.update(9L, form(null)).title()).isEqualTo("Year-end");
        assertThat(service.changeStatus(9L, EStatus.APPROVED).status()).isEqualTo(EStatus.APPROVED);
        // approved but not yet paid: no edit, no delete, no second decision
        assertCode(() -> service.update(9L, form(null)), EPayrollError.PAY_043);
        assertCode(() -> service.delete(9L), EPayrollError.PAY_043);
        assertCode(() -> service.changeStatus(9L, EStatus.REJECTED), EPayrollError.PAY_043);
        assertCode(() -> service.changeStatus(9L, EStatus.PENDING), EPayrollError.PAY_043);
        o.setStatus(EStatus.REJECTED);
        assertCode(() -> service.update(9L, form(EStatus.APPROVED)), EPayrollError.PAY_043);
        assertCode(() -> service.changeStatus(9L, EStatus.APPROVED), EPayrollError.PAY_043);
        assertCode(() -> service.delete(9L), EPayrollError.PAY_043);
        assertThat(o.isDeleted()).isFalse();
    }

    @Test
    void paidAnswersPay034AndPendingCanBeDeleted() {
        o.setStatus(EStatus.APPROVED);
        o.setPaidRunId(3L);
        assertCode(() -> service.delete(9L), EPayrollError.PAY_034);
        o.setPaidRunId(null);
        o.setStatus(EStatus.PENDING);
        service.delete(9L);
        assertThat(o.isDeleted()).isTrue();
    }

    private static OvertimeForm form(EStatus status) {
        return new OvertimeForm(" Year-end ", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, DAY, DAY, status, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EPayrollError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
