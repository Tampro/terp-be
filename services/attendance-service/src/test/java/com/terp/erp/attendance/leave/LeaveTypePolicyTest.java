package com.terp.erp.attendance.leave;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.leave.LeaveDtos.LeavePolicyForm;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeForm;
import com.terp.erp.exception.ApiException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaveTypePolicyTest {
    private final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    private final LeavePolicyRepository policies = mock(LeavePolicyRepository.class);
    private final LeaveApplicationRepository applications = mock(LeaveApplicationRepository.class);
    private final LeaveTypeService typeService = new LeaveTypeService(types, policies, applications);
    private final LeavePolicyService policyService = new LeavePolicyService(policies, types, typeService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leaveTypeDefaults() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        when(types.save(any())).thenAnswer(i -> {
            LeaveType t = i.getArgument(0);
            t.setId(1L);
            return t;
        });
        var d = typeService.create(null, new LeaveTypeForm(" Annual Leave ", null, 12, null, null, null));
        assertThat(d.name()).isEqualTo("Annual Leave");
        assertThat(d.paid()).isTrue();
        assertThat(d.color()).isEqualTo("#3B82F6");
        assertThat(d.status()).isEqualTo(EStatus.ACTIVE);
    }

    @Test
    void leaveTypeWithPoliciesCannotBeDeleted() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        when(types.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(type(10L)));
        when(policies.existsByLeaveTypeIdAndDeletedFalse(1L)).thenReturn(true);
        assertError(() -> typeService.delete(1L), EAttendanceError.ATT_038);
    }

    @Test
    void otherCompanyLeaveTypeIsNotFound() {
        TestLogin.as(11L, "LEAVE_MANAGE");
        when(types.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(type(10L)));
        assertError(() -> typeService.get(1L), EAttendanceError.ATT_034);
        assertError(() -> policyService.create(form(1, 5, null)), EAttendanceError.ATT_034);
    }

    @Test
    void policyRules() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        when(types.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(type(10L)));
        assertError(() -> policyService.create(form(6, 5, null)), EAttendanceError.ATT_037);
        when(policies.existsByLeaveTypeIdAndStatusAndDeletedFalseAndIdNot(eq(1L), eq(EStatus.ACTIVE), anyLong()))
                .thenReturn(true);
        assertError(() -> policyService.create(form(1, 5, null)), EAttendanceError.ATT_036);
        when(policies.save(any())).thenAnswer(i -> {
            LeavePolicy p = i.getArgument(0);
            p.setId(2L);
            return p;
        });
        var d = policyService.create(form(1, 5, EStatus.INACTIVE));
        assertThat(d.status()).isEqualTo(EStatus.INACTIVE);
        assertThat(d.requiresApproval()).isTrue();
        assertThat(d.leaveType().name()).isEqualTo("Annual");
    }

    @Test
    void activatingASecondPolicyIsRefused() {
        TestLogin.as(10L, "LEAVE_MANAGE");
        LeavePolicy p = new LeavePolicy();
        p.setId(2L);
        p.setCompanyId(10L);
        p.setLeaveTypeId(1L);
        p.setStatus(EStatus.INACTIVE);
        when(policies.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(p));
        when(policies.existsByLeaveTypeIdAndStatusAndDeletedFalseAndIdNot(1L, EStatus.ACTIVE, 2L)).thenReturn(true);
        assertError(() -> policyService.changeStatus(2L, EStatus.ACTIVE), EAttendanceError.ATT_036);
    }

    private static LeavePolicyForm form(int min, int max, EStatus status) {
        return new LeavePolicyForm("Standard", null, 1L, 5, min, max, null, status);
    }

    private static LeaveType type(Long companyId) {
        LeaveType t = new LeaveType();
        t.setId(1L);
        t.setCompanyId(companyId);
        t.setName("Annual");
        return t;
    }

    private static void assertError(Runnable call, EAttendanceError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
