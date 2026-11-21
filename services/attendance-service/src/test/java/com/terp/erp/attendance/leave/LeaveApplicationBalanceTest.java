package com.terp.erp.attendance.leave;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.client.EmployeeClient.Ref;
import com.terp.erp.attendance.client.MediaClient;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.leave.LeaveAppDtos.ApplicationForm;
import com.terp.erp.attendance.record.AttendanceRecordService;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.attendance.setting.AttendanceSetting;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveApplicationBalanceTest {
    private static final LocalDate FRI = LocalDate.of(2026, 12, 18);
    private static final LocalDate MON = LocalDate.of(2026, 12, 21);

    private final LeaveApplicationRepository applications = mock(LeaveApplicationRepository.class);
    private final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    private final LeavePolicyRepository policies = mock(LeavePolicyRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final LeaveBalanceSyncRepository syncs = mock(LeaveBalanceSyncRepository.class);
    private final AttendanceSettingService settings = mock(AttendanceSettingService.class);
    private final AttendanceRecordService records = mock(AttendanceRecordService.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final MediaClient media = mock(MediaClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);
    private final LeaveTypeService typeService = new LeaveTypeService(types, policies, applications);
    private final LeaveApplicationService service = new LeaveApplicationService(applications, typeService, types,
            policies, balances, settings, records, employees, media, clock);
    private final LeaveBalanceService balanceService = new LeaveBalanceService(balances, syncs, types, policies,
            applications, employees, clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void workingDaysSkipDaysOffAndBranchHolidays() {
        Holiday branchDay = new Holiday("1", "HQ day", MON, MON, false, List.of(new Ref("3", "HQ")));
        var days = LeaveApplicationService.workingDays(AttendanceSetting.DEFAULT_OFF_DAYS, List.of(branchDay), 3L, FRI,
                MON.plusDays(1));
        assertThat(days).containsExactly(FRI, MON.plusDays(1));
        var otherBranch = LeaveApplicationService.workingDays(AttendanceSetting.DEFAULT_OFF_DAYS, List.of(branchDay), 4L,
                FRI, MON.plusDays(1));
        assertThat(otherBranch).hasSize(3);
    }

    @Test
    void pendingDaysAreReservedOnTheBalance() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        stubCommon(policy(true));
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(7L, 1L, 2026)).thenReturn(Optional.of(balance("12", "8")));
        when(applications.pendingDays(eq(7L), eq(1L), any(), any(), anyLong())).thenReturn(2L);
        // 12 - 8 used - 2 pending = 2 free; Fri..Tue = 3 working days.
        assertError(() -> service.create(form(FRI, MON.plusDays(1))), EAttendanceError.ATT_047);
    }

    @Test
    void noBalanceSyncedAndPolicyLimits() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        stubCommon(policy(true));
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(7L, 1L, 2026)).thenReturn(Optional.empty());
        assertError(() -> service.create(form(FRI, FRI)), EAttendanceError.ATT_046);
        LeavePolicy strict = policy(true);
        strict.setMaxDaysPerApplication(1);
        when(policies.findFirstByLeaveTypeIdAndStatusAndDeletedFalse(1L, EStatus.ACTIVE)).thenReturn(Optional.of(strict));
        assertError(() -> service.create(form(FRI, MON)), EAttendanceError.ATT_044);
        assertError(() -> service.create(form(LocalDate.of(2026, 12, 19), LocalDate.of(2026, 12, 20))),
                EAttendanceError.ATT_043);
        assertError(() -> service.create(form(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2))),
                EAttendanceError.ATT_042);
    }

    @Test
    void withoutApprovalItIsApprovedAtOnceAndMarksAttendance() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        stubCommon(policy(false));
        LeaveBalance b = balance("12", "0");
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(7L, 1L, 2026)).thenReturn(Optional.of(b));
        when(applications.save(any())).thenAnswer(i -> {
            LeaveApplication a = i.getArgument(0);
            a.setId(5L);
            return a;
        });
        var d = service.create(form(FRI, MON));
        assertThat(d.status()).isEqualTo(ERegularizationStatus.APPROVED);
        assertThat(d.days()).isEqualTo(2);
        assertThat(b.getUsedDays()).isEqualByComparingTo("2");
        verify(records).markOnLeave(10L, 7L, FRI);
        verify(records).markOnLeave(10L, 7L, MON);
        verify(records, times(2)).markOnLeave(anyLong(), anyLong(), any());
    }

    @Test
    void approvedLeaveIsFinalAndOtherCompanyIsNotFound() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        LeaveApplication a = new LeaveApplication();
        a.setId(5L);
        a.setCompanyId(10L);
        a.setStatus(ERegularizationStatus.APPROVED);
        when(applications.findById(5L)).thenReturn(Optional.of(a));
        assertError(() -> service.delete(5L), EAttendanceError.ATT_048);
        assertError(() -> service.approve(5L, null), EAttendanceError.ATT_020);
        TestLogin.as(11L, "LEAVE_MANAGE");
        assertError(() -> service.get(5L), EAttendanceError.ATT_039);
        verify(records, never()).markOnLeave(anyLong(), anyLong(), any());
    }

    @Test
    void carryForwardIsCappedByThePolicy() {
        assertThat(LeaveBalanceService.carried(balance("12", "4"), policy(true))).isEqualByComparingTo("5");
        assertThat(LeaveBalanceService.carried(balance("12", "10"), policy(true))).isEqualByComparingTo("2");
        assertThat(LeaveBalanceService.carried(balance("12", "10"), null)).isEqualByComparingTo("0");
        assertThat(LeaveBalanceService.carried(null, policy(true))).isEqualByComparingTo("0");
    }

    @Test
    void syncWritesABalancePerEmployeeAndActiveTypeSkippingTerminated() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        when(employees.all(null)).thenReturn(List.of(employee("ACTIVE"),
                new Employee("8", "10", "EMP000008", null, "Gone", null, null, null, null, "TERMINATED", null, null)));
        when(types.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(10L, EStatus.ACTIVE)).thenReturn(List.of(type()));
        when(policies.findFirstByLeaveTypeIdAndStatusAndDeletedFalse(1L, EStatus.ACTIVE)).thenReturn(Optional.of(policy(true)));
        when(applications.approvedDays(eq(10L), any(), any())).thenReturn(List.<Object[]>of(new Object[]{7L, 1L, 3L}));
        when(balances.findByCompanyIdAndYear(10L, 2025)).thenReturn(List.of(balance("12", "9")));
        when(balances.findByCompanyIdAndYear(10L, 2026)).thenReturn(List.of());
        when(syncs.save(any())).thenAnswer(i -> {
            LeaveBalanceSync s = i.getArgument(0);
            s.setId(1L);
            return s;
        });
        var res = balanceService.sync(null, 2026);
        assertThat(res.employees()).isEqualTo(1);
        assertThat(res.balances()).isEqualTo(1);
        verify(balances).save(org.mockito.ArgumentMatchers.argThat(b -> b.getUsedDays().compareTo(new BigDecimal("3")) == 0
                && b.getCarriedForward().compareTo(new BigDecimal("3")) == 0
                && b.getAllocatedDays().compareTo(new BigDecimal("12")) == 0));
    }

    private void stubCommon(LeavePolicy p) {
        when(employees.get(7L)).thenReturn(employee("ACTIVE"));
        when(types.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(type()));
        when(types.findById(1L)).thenReturn(Optional.of(type()));
        when(policies.findFirstByLeaveTypeIdAndStatusAndDeletedFalse(1L, EStatus.ACTIVE)).thenReturn(Optional.of(p));
        when(policies.findById(2L)).thenReturn(Optional.of(p));
        when(settings.offDays(10L)).thenReturn(EnumSet.copyOf(AttendanceSetting.DEFAULT_OFF_DAYS));
        when(employees.holidays(eq(10L), any(), any())).thenReturn(List.of());
    }

    private static ApplicationForm form(LocalDate from, LocalDate to) {
        return new ApplicationForm(7L, 1L, from, to, " Trip ", null);
    }

    private static LeaveType type() {
        LeaveType t = new LeaveType();
        t.setId(1L);
        t.setCompanyId(10L);
        t.setName("Annual");
        t.setMaxDaysPerYear(12);
        return t;
    }

    private static LeavePolicy policy(boolean approval) {
        LeavePolicy p = new LeavePolicy();
        p.setId(2L);
        p.setCompanyId(10L);
        p.setLeaveTypeId(1L);
        p.setCarryForwardLimit(5);
        p.setMinDaysPerApplication(1);
        p.setMaxDaysPerApplication(10);
        p.setRequiresApproval(approval);
        return p;
    }

    private static LeaveBalance balance(String allocated, String used) {
        LeaveBalance b = new LeaveBalance();
        b.setCompanyId(10L);
        b.setEmployeeId(7L);
        b.setLeaveTypeId(1L);
        b.setYear(2026);
        b.setAllocatedDays(new BigDecimal(allocated));
        b.setUsedDays(new BigDecimal(used));
        return b;
    }

    private static Employee employee(String status) {
        return new Employee("7", "10", "EMP000007", null, "Test Person", "t@example.invalid", null, new Ref("3", "HQ"),
                null, status, null, null);
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
