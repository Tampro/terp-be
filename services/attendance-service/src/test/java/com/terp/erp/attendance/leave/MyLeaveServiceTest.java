package com.terp.erp.attendance.leave;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyLeaveServiceTest {
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveApplicationRepository applications = mock(LeaveApplicationRepository.class);
    private final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final MyLeaveService service = new MyLeaveService(balances, applications, types, employees,
            Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Employee emp(String companyId) {
        return new Employee("7", companyId, "EMP000007", null, "Test Person", null, null, null, null, "ACTIVE", null,
                null);
    }

    @Test
    void myBalancesPendingAndRecentOnlyOfMyEmployeeInMyCompany() {
        TestLogin.as(10L, "ATTENDANCE_SELF");
        when(employees.me()).thenReturn(emp("10"));
        LeaveType t = new LeaveType();
        t.setId(3L);
        t.setCompanyId(10L);
        t.setName("Annual");
        LeaveBalance b = new LeaveBalance();
        b.setCompanyId(10L);
        b.setEmployeeId(7L);
        b.setLeaveTypeId(3L);
        b.setYear(2026);
        b.setAllocatedDays(new BigDecimal("12"));
        LeaveApplication a = new LeaveApplication();
        a.setId(21L);
        a.setCompanyId(10L);
        a.setEmployeeId(7L);
        a.setLeaveTypeId(3L);
        a.setStartDate(LocalDate.of(2026, 12, 1));
        a.setEndDate(LocalDate.of(2026, 12, 2));
        a.setTotalDays(2);
        when(balances.findByCompanyIdAndYearAndEmployeeIdIn(10L, 2026, List.of(7L))).thenReturn(List.of(b));
        when(applications.findTop5ByCompanyIdAndEmployeeIdOrderByCreatedDateDescIdDesc(10L, 7L)).thenReturn(List.of(a));
        when(applications.countByCompanyIdAndEmployeeIdAndStatus(10L, 7L, ERegularizationStatus.PENDING))
                .thenReturn(1L);
        when(types.findByCompanyIdAndIdIn(any(), any())).thenReturn(List.of(t));

        var d = service.mine();

        assertThat(d.year()).isEqualTo(2026);
        assertThat(d.balances()).hasSize(1);
        assertThat(d.balances().get(0).leaveType().name()).isEqualTo("Annual");
        assertThat(d.pendingApplications()).isEqualTo(1);
        assertThat(d.recentApplications()).extracting(MyLeaveService.MyApplication::id).containsExactly("21");
    }

    @Test
    void profileOfAnotherCompanyIsNotMine() {
        TestLogin.as(10L, "ATTENDANCE_SELF");
        when(employees.me()).thenReturn(emp("11"));

        assertThatThrownBy(service::mine).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(balances, never()).findByCompanyIdAndYearAndEmployeeIdIn(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                any());
    }
}
