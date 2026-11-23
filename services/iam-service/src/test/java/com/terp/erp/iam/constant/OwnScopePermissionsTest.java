package com.terp.erp.iam.constant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OwnScopePermissionsTest {
    private static final String SEED =
            "db/changelog/changes/20261123200000_own_scope_permission_init_data.mysql.sql";

    @Test
    void everyConstantIsSeeded() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SEED)) {
            assertThat(in).as(SEED).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        OwnPermissions.ALL.forEach(c -> assertThat(sql).contains("'" + c + "'"));
        PortalPermissions.ALL.forEach(c -> assertThat(sql).contains("'" + c + "', 'PORTAL'"));
    }

    @Test
    void everyOwnCodeHasABaseCode() {
        java.util.Set<String> bases = new java.util.HashSet<>(SalesPermissions.ALL);
        bases.addAll(SupportPermissions.ALL);
        OwnPermissions.ALL.forEach(c -> {
            assertThat(c).endsWith("_OWN");
            assertThat(bases).contains(c.substring(0, c.length() - 4));
        });
    }

    @Test
    void companyAdminHoldsEveryOwnAndPortalCode() {
        assertThat(EDefaultRole.COMPANY_ADMIN.getPermissions())
                .containsAll(OwnPermissions.ALL).containsAll(PortalPermissions.ALL)
                .containsAll(SalesPermissions.ALL).containsAll(SupportPermissions.ALL);
    }

    @Test
    void defaultRoleGrants() {
        assertThat(EDefaultRole.CLIENT.getPermissions()).containsExactlyInAnyOrder(PortalPermissions.CLIENT,
                OwnPermissions.SUPPORT_TICKET_VIEW_OWN, OwnPermissions.SUPPORT_TICKET_MANAGE_OWN);
        assertThat(EDefaultRole.CLIENT.getPermissions()).doesNotContain(SupportPermissions.MESSENGER_USE);
        assertThat(EDefaultRole.VENDOR.getPermissions()).containsExactly(PortalPermissions.VENDOR);
        assertThat(EDefaultRole.MANAGER.getPermissions()).containsExactlyInAnyOrderElementsOf(
                union(OwnPermissions.ALL, AiPermissions.ALL));
        assertThat(EDefaultRole.EMPLOYEE.getPermissions()).contains("ATTENDANCE_SELF", "EMPLOYEE_SELF",
                OwnPermissions.PROJECT_VIEW_OWN, OwnPermissions.SUPPORT_TICKET_MANAGE_OWN);
        assertThat(EDefaultRole.EXECUTIVE.getPermissions()).allMatch(c -> c.endsWith("_VIEW_OWN"));
        // No default role besides the admin holds a full commercial code next to its own variant.
        for (EDefaultRole r : EDefaultRole.values()) {
            if (r != EDefaultRole.COMPANY_ADMIN) {
                assertThat(r.getPermissions()).doesNotContain("SALES_VIEW", "CRM_VIEW", "PROJECT_VIEW");
            }
        }
    }

    private static java.util.Set<String> union(java.util.Set<String> a, java.util.Set<String> b) {
        java.util.Set<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return all;
    }
}
