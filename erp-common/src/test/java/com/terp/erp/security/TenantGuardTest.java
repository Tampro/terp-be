package com.terp.erp.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantGuardTest {

    @AfterEach
    void clear() {
        TestPrincipals.logout();
    }

    @Test
    void companyUserAlwaysActsOnOwnCompanyEvenWhenAnotherIsRequested() {
        TestPrincipals.login(TestPrincipals.companyUser(1, 10));
        assertThat(TenantGuard.resolveCompany(99L, ECommonError.COMMON_005)).isEqualTo(10L);
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestPrincipals.login(TestPrincipals.superAdmin());
        assertThat(TenantGuard.resolveCompany(99L, ECommonError.COMMON_005)).isEqualTo(99L);
        assertThatThrownBy(() -> TenantGuard.resolveCompany(null, ECommonError.COMMON_005))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rowOfAnotherCompanyIsNotFound() {
        TestPrincipals.login(TestPrincipals.companyUser(1, 10));
        assertThatThrownBy(() -> TenantGuard.assertSameCompany(11L, ECommonError.COMMON_005))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        TenantGuard.assertSameCompany(10L, ECommonError.COMMON_005);
    }

    @Test
    void userWithoutCompanyReachesNoRow() {
        TestPrincipals.login(new ErpPrincipal(null, "kc-x", "x", null, false, java.util.Set.of(), java.util.Set.of()));
        assertThatThrownBy(() -> TenantGuard.assertSameCompany(null, ECommonError.COMMON_005))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void permissionCheckerReadsResolvedPermissions() {
        PermissionChecker checker = new PermissionChecker();
        TestPrincipals.login(TestPrincipals.companyUser(1, 10, "IAM_ROLE_VIEW"));
        assertThat(checker.has("IAM_ROLE_VIEW")).isTrue();
        assertThat(checker.has("IAM_ROLE_DELETE")).isFalse();
        assertThat(checker.isSuperAdmin()).isFalse();

        TestPrincipals.login(TestPrincipals.superAdmin());
        assertThat(checker.has("ANYTHING")).isTrue();
        assertThat(checker.hasInCompany("ANYTHING")).isFalse();

        TestPrincipals.logout();
        assertThat(checker.has("IAM_ROLE_VIEW")).isFalse();
    }
}
