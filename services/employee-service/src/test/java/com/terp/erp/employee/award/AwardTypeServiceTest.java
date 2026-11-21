package com.terp.erp.employee.award;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeForm;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwardTypeServiceTest {
    private final AwardTypeRepository types = mock(AwardTypeRepository.class);
    private final AwardTypeService service = new AwardTypeService(types,
            org.mockito.Mockito.mock(com.terp.erp.employee.lifecycle.EmployeeAwardRepository.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTrimsAndDefaultsToActive() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(types.save(any())).thenAnswer(i -> {
            AwardType t = i.getArgument(0);
            t.setId(3L);
            return t;
        });
        var d = service.create(null, new AwardTypeForm(" Star ", " ", "trophy", null));
        assertThat(d.name()).isEqualTo("Star");
        assertThat(d.description()).isNull();
        assertThat(d.icon()).isEqualTo("trophy");
        assertThat(d.status()).isEqualTo(EOrgStatus.ACTIVE);
        assertThat(d.companyId()).isEqualTo("10");
    }

    @Test
    void nameTakenInTheCompany() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(types.nameTaken(10L, "Star", -1L)).thenReturn(true);
        assertError(() -> service.create(null, new AwardTypeForm("Star", null, null, null)), EEmployeeError.EMP_011);
    }

    @Test
    void updateIsFullReplacementButKeepsOmittedStatus() {
        TestLogin.as(10L, "ORG_MANAGE");
        AwardType t = type(10L);
        t.setStatus(EOrgStatus.INACTIVE);
        t.setDescription("old");
        when(types.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(t));
        var d = service.update(3L, new AwardTypeForm("Star 2", null, null, null));
        assertThat(d.description()).isNull();
        assertThat(d.status()).isEqualTo(EOrgStatus.INACTIVE);
    }

    @Test
    void otherCompanyTypeIsNotFound() {
        TestLogin.as(11L, "ORG_MANAGE");
        when(types.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(type(10L)));
        assertError(() -> service.delete(3L), EEmployeeError.EMP_042);
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertError(() -> service.options(null), EEmployeeError.EMP_019);
    }

    private static AwardType type(Long companyId) {
        AwardType t = new AwardType();
        t.setId(3L);
        t.setCompanyId(companyId);
        t.setName("Star");
        return t;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
