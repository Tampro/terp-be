package com.terp.erp.attendance.policy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.attendance.TestLogin;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendancePolicyServiceTest {
    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private final AttendancePolicyService service = new AttendancePolicyService(policies);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void omittedNumbersTakeTheDefaultsOfDoc03() {
        TestLogin.as(10L, "ATTENDANCE_MANAGE");
        when(policies.save(any())).thenAnswer(i -> {
            AttendancePolicy p = i.getArgument(0);
            p.setId(2L);
            return p;
        });
        var d = service.create(null, new PolicyForm(" Standard ", null, null, null, null, new BigDecimal("50000"), null));
        assertThat(d.name()).isEqualTo("Standard");
        assertThat(d.lateArrivalGraceMinutes()).isEqualTo(15);
        assertThat(d.earlyDepartureGraceMinutes()).isEqualTo(15);
        assertThat(d.halfDayThresholdHours()).isEqualByComparingTo("4");
    }

    @Test
    void summaryAveragesAndEmptyCompany() {
        TestLogin.as(10L, "ATTENDANCE_VIEW");
        when(policies.summary(10L)).thenReturn(List.<Object[]>of(new Object[]{3L, 2L, 16.6667d, new BigDecimal("175")}));
        var s = service.summary(null);
        assertThat(s.avgLateArrivalGraceMinutes()).isEqualByComparingTo("16.67");
        assertThat(s.avgOvertimeRatePerHour()).isEqualByComparingTo("175.00");
        when(policies.summary(10L)).thenReturn(List.<Object[]>of(new Object[]{0L, 0L, null, null}));
        assertThat(service.summary(null).avgOvertimeRatePerHour()).isEqualByComparingTo("0");
    }

    @Test
    void otherCompanyPolicyIsNotFound() {
        TestLogin.as(11L, "ATTENDANCE_MANAGE");
        AttendancePolicy p = new AttendancePolicy();
        p.setId(2L);
        p.setCompanyId(10L);
        when(policies.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EAttendanceError.ATT_002);
    }
}
