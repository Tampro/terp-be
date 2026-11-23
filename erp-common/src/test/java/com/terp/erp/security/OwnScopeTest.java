package com.terp.erp.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnScopeTest {

    @AfterEach
    void clear() {
        TestPrincipals.logout();
    }

    @Test
    void fullCodeWinsOverOwnCode() {
        TestPrincipals.login(TestPrincipals.companyUser(1, 10, "SALES_VIEW", "SALES_VIEW_OWN"));
        assertThat(OwnScope.resolve("SALES_VIEW", "SALES_VIEW_OWN")).isEqualTo(OwnScope.Mode.ALL);
        assertThat(OwnScope.ownOnly("SALES_VIEW", "SALES_VIEW_OWN")).isFalse();
    }

    @Test
    void ownCodeAloneIsOwnMode() {
        TestPrincipals.login(TestPrincipals.companyUser(1, 10, "SALES_VIEW_OWN"));
        assertThat(OwnScope.resolve("SALES_VIEW", "SALES_VIEW_OWN")).isEqualTo(OwnScope.Mode.OWN);
        assertThat(OwnScope.ownOnly("SALES_VIEW", "SALES_VIEW_OWN")).isTrue();
        assertThat(OwnScope.userId()).isEqualTo(1L);
        assertThat(OwnScope.sub()).isEqualTo("kc-1");
    }

    @Test
    void neitherCodeIsForbidden() {
        TestPrincipals.login(TestPrincipals.companyUser(1, 10, "CRM_VIEW"));
        assertThatThrownBy(() -> OwnScope.resolve("SALES_VIEW", "SALES_VIEW_OWN"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ECommonError.COMMON_004);
                });
    }

    @Test
    void superAdminSeesAll() {
        TestPrincipals.login(TestPrincipals.superAdmin());
        assertThat(OwnScope.resolve("SALES_VIEW", "SALES_VIEW_OWN")).isEqualTo(OwnScope.Mode.ALL);
        assertThat(OwnScope.userId()).isNull();
    }

    @Test
    void noCallerIsUnauthorized() {
        assertThatThrownBy(() -> OwnScope.resolve("SALES_VIEW", "SALES_VIEW_OWN"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void assertMineAnswersNotFound() {
        OwnScope.assertMine(true, ECommonError.COMMON_005);
        assertThatThrownBy(() -> OwnScope.assertMine(false, ECommonError.COMMON_005))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
